package ca.ubc.cs.cpsc210.meetup.map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.ResourceProxy;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.OverlayManager;
import org.osmdroid.views.overlay.PathOverlay;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import ca.ubc.cs.cpsc210.meetup.R;
import ca.ubc.cs.cpsc210.meetup.exceptions.IllegalCourseTimeException;
import ca.ubc.cs.cpsc210.meetup.model.Building;
import ca.ubc.cs.cpsc210.meetup.model.Course;
import ca.ubc.cs.cpsc210.meetup.model.CourseFactory;
import ca.ubc.cs.cpsc210.meetup.model.EatingPlace;
import ca.ubc.cs.cpsc210.meetup.model.Place;
import ca.ubc.cs.cpsc210.meetup.model.PlaceFactory;
import ca.ubc.cs.cpsc210.meetup.model.Section;
import ca.ubc.cs.cpsc210.meetup.model.Student;
import ca.ubc.cs.cpsc210.meetup.model.StudentManager;
import ca.ubc.cs.cpsc210.meetup.util.CourseTime;
import ca.ubc.cs.cpsc210.meetup.util.LatLon;
import ca.ubc.cs.cpsc210.meetup.util.SchedulePlot;

/**
 * Fragment holding the map in the UI.
 */
public class MapDisplayFragment extends Fragment {

    /**
     * Log tag for LogCat messages
     */
    private final static String LOG_TAG = "MapDisplayFragment";
    /**
     * A central location in campus that might be handy.
     */
    private final static GeoPoint UBC_BUS_LOOP = new GeoPoint(49.2686759, -123.2478716);
    /**
     * FourSquare URLs. You must complete the client_id and client_secret with values
     * you sign up for.
     */
    private static String FOUR_SQUARE_URL = "https://api.foursquare.com/v2/venues/explore";
    private static String FOUR_SQUARE_CLIENT_ID =
            "X3ZBTFX52UUN04PKSSUPW50SP4PEPQ2Y5CSNSPIOX4GPDT4E";
    private static String FOUR_SQUARE_CLIENT_SECRET =
            "RELHKWDJMVYI2QDEVTA5TGUUUNJN4KLMILZCU0EFUGEJJEXI";
    private static String MAP_QUEST_APP_KEY = "Fmjtd%7Cluu82l0bnd%2C22%3Do5-94zlda";
    private static int ME_ID = 23002090;
    /**
     * Meetup Service URL
     * CPSC 210 Students: Complete the string.
     */
    private final String getStudentURL = "http://kramer.nss.cs.ubc.ca:8081/getStudent";
    /**
     * Preference manager to access user preferences
     */
    private SharedPreferences sharedPreferences;
    //might be useful later.
    // private static String MAP_QUEST_URL = "http://www.mapquestapi.com/directions/v2/route?key=" + MAP_QUEST_APP_KEY + "&from=Lancaster,PA&to=York,PA&callback=renderNarrative";
    /**
     * String to know whether we are dealing with MWF or TR schedule.
     * You will need to update this string based on the settings dialog at appropriate
     * points in time. See the project page for details on how to access
     * the value of a setting.
     */
    private String activeDay = "MWF";
    /**
     * Overlays for displaying my schedules, buildings, etc.
     */
    private List<PathOverlay> scheduleOverlay;
    private ItemizedIconOverlay<OverlayItem> buildingOverlay;
    private OverlayItem selectedBuildingOnMap;
    /**
     * View that shows the map
     */
    private MapView mapView;
    /**
     * Access to domain model objects. Only store "me" in the studentManager for
     * the base project (i.e., unless you are doing bonus work).
     */
    private StudentManager studentManager;
    private Student randomStudent;
    private Student me;
    /**
     * Map controller for zooming in/out, centering
     */
    private IMapController mapController;

    // ******************** Android methods for starting, resuming, ...

    // You should not need to touch this method
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
        sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(getActivity());
        scheduleOverlay = new ArrayList<PathOverlay>();

        // You need to setup the courses for the app to know about. Ideally
        // we would access a web service like the UBC student information system
        // but that is not currently possible
        initializeCourses();

        // Initialize the data for the "me" schedule. Note that this will be
        // hard-coded for now
        initializeMySchedule();

        // get places from JSON
        //initializePlaces();

        // You are going to need an overlay to draw buildings and locations on the map
        buildingOverlay = createBuildingOverlay();
    }

    // You should not need to touch this method
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK)
            return;
    }

    // You should not need to touch this method
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        //initializeMySchedule();
        //initializePlaces();

        if (mapView == null) {
            mapView = new MapView(getActivity(), null);

            mapView.setTileSource(TileSourceFactory.MAPNIK);
            mapView.setClickable(true);
            mapView.setBuiltInZoomControls(true);
            mapView.setMultiTouchControls(true);

            mapController = mapView.getController();
            mapController.setZoom(mapView.getMaxZoomLevel() - 2);
            mapController.setCenter(UBC_BUS_LOOP);
        }

        return mapView;
    }

    // You should not need to touch this method
    @Override
    public void onDestroyView() {
        Log.d(LOG_TAG, "onDestroyView");
        ((ViewGroup) mapView.getParent()).removeView(mapView);
        super.onDestroyView();
    }

    // You should not need to touch this method
    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
        super.onDestroy();
    }

    // You should not need to touch this method
    @Override
    public void onResume() {
        Log.d(LOG_TAG, "onResume");
        super.onResume();
    }

    // You should not need to touch this method
    @Override
    public void onPause() {
        Log.d(LOG_TAG, "onPause");
        super.onPause();
    }

    /**
     * Save map's zoom level and centre. You should not need to
     * touch this method
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.d(LOG_TAG, "onSaveInstanceState");
        super.onSaveInstanceState(outState);

        if (mapView != null) {
            outState.putInt("zoomLevel", mapView.getZoomLevel());
            IGeoPoint cntr = mapView.getMapCenter();
            outState.putInt("latE6", cntr.getLatitudeE6());
            outState.putInt("lonE6", cntr.getLongitudeE6());
            Log.i("MapSave", "Zoom: " + mapView.getZoomLevel());
        }
    }

    // ****************** App Functionality

    /**
     * Show my schedule on the map. Every time "me"'s schedule shows, the map
     * should be cleared of all existing schedules, buildings, meetup locations, etc.
     */
    public void showMySchedule() {

        // CPSC 210 Students: You must complete the implementation of this method.
        // The very last part of the method should call the asynchronous
        // task (which you will also write the code for) to plot the route
        // for "me"'s schedule for the day of the week set in the Settings
        clearSchedules();
        String dayOfWeek = sharedPreferences.getString("dayOfWeek", "MWF");
        String name = me.getFirstName() +
                " " + me.getLastName();


        // Asynchronous tasks are a bit onerous to deal with. In order to provide
        // all information needed in one object to plot "me"'s route, we
        // create a SchedulePlot object and pass it to the asynchrous task.
        // See the project page for more details.
        SchedulePlot mySchedulePlot = new SchedulePlot(me.getSchedule().getSections(dayOfWeek),
                name, "#DF0101", R.drawable.ic_action_place);

        // Get a routing between these points. This line of code creates and calls
        // an asynchronous task to do the calls to MapQuest to determine a route
        // and plots the route.
        // Assumes mySchedulePlot is a created and initialized SchedulePlot object


        new GetRoutingForSchedule().execute(mySchedulePlot);


    }

    /**
     * Retrieve a random student's schedule from the Meetup web service and
     * plot a route for the schedule on the map. The plot should be for
     * the given day of the week as determined when "me"'s schedule
     * was plotted.
     */
    public void showRandomStudentsSchedule() {
        // To get a random student's schedule, we have to call the MeetUp web service.
        // Calling this web service requires a network access to we have to
        // do this in an asynchronous task. See below in this class for where
        // you need to implement methods for performing the network access
        // and plotting.

        clearSchedules();
        showMySchedule();
        new GetRandomSchedule().execute();
    }

    /**
     * Clear all schedules on the map
     */
    public void clearSchedules() {
        randomStudent = null;
        OverlayManager om = mapView.getOverlayManager();
        om.clear();
        scheduleOverlay.clear();
        buildingOverlay.removeAllItems();
        om.addAll(scheduleOverlay);
        om.add(buildingOverlay);
        mapView.invalidate();
    }

    /**
     * Find all possible locations at which "me" and random student could meet
     * up for the set day of the week and the set time to meet and the set
     * distance either "me" or random is willing to travel to meet.
     * A meetup is only possible if both "me" and random are free at the
     * time specified in the settings and each of us must have at least an hour
     * (>= 60 minutes) free. You should display dialog boxes if there are
     * conditions under which no meetup can happen (e.g., me or random is
     * in class at the specified time)
     */
    public void findMeetupPlace() {

        String dayOfWeek = sharedPreferences.getString("dayOfWeek", "MWF");
        String timeOfDay = sharedPreferences.getString("timeOfDay", "12:00");
        int placeDistance = Integer.parseInt(sharedPreferences.getString("placeDistance", "250"));


        // Do I and randomStudent have class today?
        try {
            Set<Section> myClassToday = me.getSchedule().getSections(dayOfWeek);

            if (myClassToday.isEmpty()) {
                AlertDialog aDialog = createSimpleDialog("you don't have class today!");
                aDialog.show();
                //backs out of the method
                return;
            }
            Set<Section> randomClassToday = randomStudent.getSchedule().getSections(dayOfWeek);
            if (randomClassToday.isEmpty()) {
                AlertDialog aDialog = createSimpleDialog(randomStudent.getFirstName() + " doesn't have class today!");
                aDialog.show();
                //backs out of the method
                return;
            }
        } catch (NullPointerException e) {
            AlertDialog aDialog = createSimpleDialog("please find a random student to meet up.");
            aDialog.show();
            //back
            return;
        }
        // is the meetup time before or after we're on campus?
        try {
            CourseTime meetUpCourseTime = new CourseTime(timeOfDay + ":00", timeOfDay + ":00");
            CourseTime myEarliestCourse = me.getSchedule().startTime(dayOfWeek);
            CourseTime myLatestCourseTime = me.getSchedule().endTime(dayOfWeek);
            //is the meetupTime before my earliest course?
            if (meetUpCourseTime.compareTo(myEarliestCourse) < 0) {
                AlertDialog aDialog = createSimpleDialog("you're not on campus yet.");
                aDialog.show();
                //back
                return;
            }//is the meetup time after my latest course?
            if (myLatestCourseTime.compareTo(meetUpCourseTime) < 0) {
                AlertDialog aDialog = createSimpleDialog("you already left campus.");
                aDialog.show();
                //back
                return;
            }

            CourseTime randomEarliestCourse = randomStudent.getSchedule().startTime(dayOfWeek);
            CourseTime randomLatestCourse = randomStudent.getSchedule().endTime(dayOfWeek);
            //is the meetupTime before random's earliest course?
            if (meetUpCourseTime.compareTo(randomEarliestCourse) < 0) {
                AlertDialog aDialog = createSimpleDialog(randomStudent.getFirstName() + " is not on campus yet.");
                aDialog.show();
                //back
                return;
            }//has random already left campus?
            if (randomLatestCourse.compareTo(meetUpCourseTime) < 0) {
                AlertDialog aDialog = createSimpleDialog(randomStudent.getFirstName() + " has already left campus.");
                aDialog.show();
                //back
                return;
            }

        } catch (IllegalCourseTimeException e) {
            e.printStackTrace();
        }


        //now that we both have class: do i have a break right now?
        Boolean iHaveBreak = haveBreak(me, dayOfWeek, timeOfDay);
        if (!iHaveBreak) {
            AlertDialog aDialog = createSimpleDialog("you can't meet up right now!");
            aDialog.show();
            //backs out of the method
            return;
        }
        //ditto for random.
        Boolean randomHaveBreak = haveBreak(randomStudent, dayOfWeek, timeOfDay);
        if (!randomHaveBreak) {
            AlertDialog aDialog = createSimpleDialog(randomStudent.getFirstName() + " can't meet up right now!");
            aDialog.show();
            //backs out of the method
            return;
        }

        PlaceFactory placeFactory = PlaceFactory.getInstance();
        if (PlaceFactory.getInstance().getPlaces().isEmpty()) {
            AlertDialog aDialog = createSimpleDialog("please get places first.");
            aDialog.show();
            //exit the method.
            return;
        }

        Building meBuilding;
        Building randomBuilding;

        //use helper to find closest break time to the time selected.
        String breakTime = breakTime(me, dayOfWeek, timeOfDay);

        meBuilding = me.getSchedule().whereAmI(dayOfWeek, breakTime);

        if (meBuilding == null) {
            AlertDialog aDialog = createSimpleDialog("mebuilding is empty. Weird.");
            aDialog.show();
            //exit the method.
            return;
        }
        randomBuilding = randomStudent.getSchedule().whereAmI(dayOfWeek, breakTime);
        if (meBuilding == null) {
            breakTime = breakTime(randomStudent, dayOfWeek, timeOfDay);
            randomBuilding = randomStudent.getSchedule().whereAmI(dayOfWeek, breakTime);
        }

        //find the middleground between you and randomstudent.
        Double medLat = (meBuilding.getLatLon().getLatitude() + randomBuilding.getLatLon().getLatitude()) / 2;
        Double medLng = (meBuilding.getLatLon().getLongitude() + randomBuilding.getLatLon().getLongitude()) / 2;
        LatLon median = new LatLon(medLat, medLng);

        Set<Place> allMeetPlaces = placeFactory.findPlacesWithinDistance(median, placeDistance);
        //all the places within the radius of the midpoint.
        List<Place> meetPlaces = new ArrayList<Place>();

        //make sure meeting place is within the distance for both me and random student.
        for (Place s : allMeetPlaces) {
            Double distance1 = LatLon.distanceBetweenTwoLatLon(meBuilding.getLatLon(), s.getLatLon());
            Double distance2 = LatLon.distanceBetweenTwoLatLon(randomBuilding.getLatLon(), s.getLatLon());

            if (distance1 <= placeDistance && distance2 <= placeDistance) {
                meetPlaces.add(s);
            }
        }

        if (meetPlaces.isEmpty()) {
            AlertDialog aDialog = createSimpleDialog("no places found. Try expanding your search distance!");
            aDialog.show();
            //exits method
            return;
        }
        for (Place p : meetPlaces) {
            Building pBuilding = new Building(p.getName(), p.getLatLon());
            plotABuilding(pBuilding, p.getName(), p.getDisPlayText(), R.drawable.ic_launcher);


        }
        //redraw the map
        mapView.invalidate();
        AlertDialog aDialog = createSimpleDialog("found " + meetPlaces.size() + " places to meet!");
        aDialog.show();

    }

    /**
     * helper method that finds the time to use for whereAmI method
     *
     * @param student
     * @param dayOfWeek in "MWF" or "TR"
     * @param timeOfDay "10", "12", "15", or "18".
     * @return returns the earliest available break for that time of day.
     */
    private String breakTime(Student student, String dayOfWeek, String timeOfDay) {
        List<String> dayBreaks = new ArrayList<String>();

        try {
            dayBreaks = new ArrayList(student.getSchedule().getStartTimesOfBreaks(dayOfWeek));
        } catch (NullPointerException e) {
            AlertDialog aDialog = createSimpleDialog("please initialize random schedule.");
            aDialog.show();
        }
        List<String> toRemove = new ArrayList<String>();


        int timeInMinutes = Integer.parseInt(timeOfDay) * 60;

        for (String s : dayBreaks) {
            int breakInMinute = Integer.parseInt(s.substring(0, 1)) * 60 + Integer.parseInt(s.substring(3));
            int difference = Math.abs(breakInMinute - timeInMinutes);

            if (difference < 60) {
                toRemove.add(s);
            }

        }
        dayBreaks.removeAll(toRemove);
        return dayBreaks.get(0);
    }

    /**
     * helper method to findMeetUpPlace(); determines
     * if a student has a break that starts within an hour of the indicated time.
     * @param student
     * @param dayOfWeek in "MWF" or "TR"
     * @param timeOfDay "10", "12", "15", or "18".
     * @return True if student has break at that time; False otherwise.
     */
    private Boolean haveBreak(Student student, String dayOfWeek, String timeOfDay) {
        Set<String> daySchedule = new TreeSet<String>();
        Boolean haveBreak;

        try {
            daySchedule = student.getSchedule().getStartTimesOfBreaks(dayOfWeek);
        } catch (NullPointerException e) {
            AlertDialog aDialog = createSimpleDialog("please initialize random schedule.");
            aDialog.show();
            haveBreak = false;
        }
        List<String> toRemove = new ArrayList<String>();


        int timeInMinutes = Integer.parseInt(timeOfDay) * 60;

        for (String s : daySchedule) {
            int breakInMinute = Integer.parseInt(s.substring(0, 1)) * 60 + Integer.parseInt(s.substring(3));
            int difference = Math.abs(breakInMinute - timeInMinutes);

            if (difference < 60) {
                toRemove.add(s);
            }

        }
        daySchedule.removeAll(toRemove);
        if (daySchedule.isEmpty()) {
            haveBreak = false;
        } else {
            haveBreak = true;
        }


        return haveBreak;
    }


    /**
     * Initialize the PlaceFactory with information from FourSquare
     */
    public void initializePlaces() {
        // CPSC 210 Students: You should not need to touch this method, but
        // you will have to implement GetPlaces below.
        new GetPlaces().execute();
    }


    /**
     * Plot all buildings referred to in the given information about plotting
     * a schedule.
     *
     * @param schedulePlot All information about the schedule and route to plot.
     */
    private void plotBuildings(SchedulePlot schedulePlot) {

        List<Section> classesToPlot = new ArrayList(schedulePlot.getSections());
        String Name = schedulePlot.getName();


        for (Section s : classesToPlot) {
            String className = s.getCourse().getCode() + s.getCourse().getNumber();
            String classTime = "Section " + s.getName() + ", " + s.getCourseTime().toString();

            plotABuilding(s.getBuilding(), Name + ", " + className, classTime, schedulePlot.getIcon());
        }

//        String connections = "contains bays for busses 99 B-Line, 84, 44, 480, 41,43,49, 33, 25, C18, C19 and C20.";
//        OverlayItem busLoop = new OverlayItem("UBC Bus Loop", connections, UBC_BUS_LOOP);
//        buildingOverlay.addItem(busLoop);


        // CPSC 210 Students: You will need to ensure the buildingOverlay is in
        // the overlayManager. The following code achieves this. You should not likely
        // need to touch it
//        OverlayManager om = mapView.getOverlayManager();
//        om.add(buildingOverlay);

    }


    /**
     * Plot a building onto the map
     *
     * @param building      The building to put on the map
     * @param title         The title to put in the dialog box when the building is tapped on the map
     * @param msg           The message to display when the building is tapped
     * @param drawableToUse The icon to use. Can be R.drawable.ic_action_place (or any icon in the res/drawable directory)
     */
    private void plotABuilding(Building building, String title, String msg, int drawableToUse) {
        // CPSC 210 Students: You should not need to touch this method

        //Math.random makes sure there's a different plot for two classes in the same building.

        OverlayItem buildingItem = new OverlayItem(title, msg,
                new GeoPoint(building.getLatLon().getLatitude() - ((1.8 + Math.random()) / 3) * 0.0003,
                        building.getLatLon().getLongitude() + ((1.8 + Math.random()) / 3) * 0.0003)); //

        //Create new marker
        Drawable icon = this.getResources().getDrawable(drawableToUse);

        //Set the bounding for the drawable
        icon.setBounds(
                0 - icon.getIntrinsicWidth() / 2, 0 - icon.getIntrinsicHeight(),
                icon.getIntrinsicWidth() / 2, 0);

        //Set the new marker to the overlay
        buildingItem.setMarker(icon);
        buildingOverlay.addItem(buildingItem);
    }


    /**
     * Initialize your schedule by coding it directly in. This is the schedule
     * that will appear on the map when you select "Show My Schedule".
     */
    private void initializeMySchedule() {

        studentManager = new StudentManager();
        studentManager.addStudent("Liu", "James", ME_ID);
        me = studentManager.get(ME_ID);

        // Log.d("creating a student,", me.getFirstName());

        studentManager.addSectionToSchedule(ME_ID, "CPSC", 210, "201");
        studentManager.addSectionToSchedule(ME_ID, "MATH", 200, "201");
        // studentManager.addSectionToSchedule(ME_ID, "ENGL", 222, "007");
        studentManager.addSectionToSchedule(ME_ID, "MATH", 221, "202");
        studentManager.addSectionToSchedule(ME_ID, "BIOL", 201, "201");


    }

    /**
     * Helper to create simple alert dialog to display message
     *
     * @param msg message to display in alert dialog
     * @return the alert dialog
     */
    private AlertDialog createSimpleDialog(String msg) {
        // CPSC 210 Students; You should not need to modify this method
        AlertDialog.Builder dialogBldr = new AlertDialog.Builder(getActivity());
        dialogBldr.setMessage(msg);
        dialogBldr.setNeutralButton(R.string.ok, null);

        return dialogBldr.create();
    }

    /**
     * Create the overlay used for buildings. CPSC 210 students, you should not need to
     * touch this method.
     *
     * @return An overlay
     */
    private ItemizedIconOverlay<OverlayItem> createBuildingOverlay() {
        ResourceProxy rp = new DefaultResourceProxyImpl(getActivity());

        ItemizedIconOverlay.OnItemGestureListener<OverlayItem> gestureListener =
                new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {

                    /**
                     * Display building description in dialog box when user taps stop.
                     *
                     * @param index
                     *            index of item tapped
                     * @param oi
                     *            the OverlayItem that was tapped
                     * @return true to indicate that tap event has been handled
                     */
                    @Override
                    public boolean onItemSingleTapUp(int index, OverlayItem oi) {

                        new AlertDialog.Builder(getActivity())
                                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface arg0, int arg1) {
                                        if (selectedBuildingOnMap != null) {
                                            mapView.invalidate();
                                        }
                                    }
                                }).setTitle(oi.getTitle()).setMessage(oi.getSnippet())
                                .show();

                        selectedBuildingOnMap = oi;
                        mapView.invalidate();
                        return true;
                    }

                    @Override
                    public boolean onItemLongPress(int index, OverlayItem oi) {
                        // do nothing
                        return false;
                    }
                };

        return new ItemizedIconOverlay<OverlayItem>(
                new ArrayList<OverlayItem>(), getResources().getDrawable(
                R.drawable.ic_action_place), gestureListener, rp);
    }


    /**
     * Create overlay with a specific color
     *
     * @param colour A string with a hex colour value
     */
    private PathOverlay createPathOverlay(String colour) {
        // CPSC 210 Students, you should not need to touch this method
        PathOverlay po = new PathOverlay(Color.parseColor(colour),
                getActivity());
        Paint pathPaint = new Paint();
        pathPaint.setColor(Color.parseColor(colour));
        pathPaint.setStrokeWidth(4.0f);
        pathPaint.setStyle(Paint.Style.STROKE);
        po.setPaint(pathPaint);
        return po;
    }

    // *********************** Asynchronous tasks

    /**
     * Initialize the CourseFactory with some courses.
     */
    private void initializeCourses() {
        // CPSC 210 Students: You can change this data if you desire.
        CourseFactory courseFactory = CourseFactory.getInstance();

        Building dmpBuilding = new Building("DMP", new LatLon(49.261474, -123.248060));

        Course cpsc210 = courseFactory.getCourse("CPSC", 210);
        Section aSection = new Section("202", "MWF", "12:00", "12:50", dmpBuilding);
        cpsc210.addSection(aSection);
        aSection.setCourse(cpsc210);
        aSection = new Section("201", "MWF", "16:00", "16:50", dmpBuilding);
        cpsc210.addSection(aSection);
        aSection.setCourse(cpsc210);
        aSection = new Section("BCS", "MWF", "12:00", "12:50", dmpBuilding);
        cpsc210.addSection(aSection);
        aSection.setCourse(cpsc210);

        Course engl222 = courseFactory.getCourse("ENGL", 222);
        aSection = new Section("007", "MWF", "14:00", "14:50", new Building("Buchanan", new LatLon(49.269258, -123.254784)));
        engl222.addSection(aSection);
        aSection.setCourse(engl222);

        Course scie220 = courseFactory.getCourse("SCIE", 220);
        aSection = new Section("200", "MWF", "15:00", "15:50", new Building("Swing", new LatLon(49.262786, -123.255044)));
        scie220.addSection(aSection);
        aSection.setCourse(scie220);

        Course math200 = courseFactory.getCourse("MATH", 200);
        aSection = new Section("201", "MWF", "09:00", "09:50", new Building("Buchanan", new LatLon(49.269258, -123.254784)));
        math200.addSection(aSection);
        aSection.setCourse(math200);

        Course fren102 = courseFactory.getCourse("FREN", 102);
        aSection = new Section("202", "MWF", "11:00", "11:50", new Building("Barber", new LatLon(49.267442, -123.252471)));
        fren102.addSection(aSection);
        aSection.setCourse(fren102);

        Course japn103 = courseFactory.getCourse("JAPN", 103);
        aSection = new Section("002", "MWF", "10:00", "11:50", new Building("Buchanan", new LatLon(49.269258, -123.254784)));
        japn103.addSection(aSection);
        aSection.setCourse(japn103);

        Course scie113 = courseFactory.getCourse("SCIE", 113);
        aSection = new Section("213", "MWF", "13:00", "13:50", new Building("Swing", new LatLon(49.262786, -123.255044)));
        scie113.addSection(aSection);
        aSection.setCourse(scie113);

        Course micb308 = courseFactory.getCourse("MICB", 308);
        aSection = new Section("201", "MWF", "12:00", "12:50", new Building("Woodward", new LatLon(49.264704, -123.247536)));
        micb308.addSection(aSection);
        aSection.setCourse(micb308);

        Course math221 = courseFactory.getCourse("MATH", 221);
        aSection = new Section("202", "TR", "11:00", "12:20", new Building("Klinck", new LatLon(49.266112, -123.254776)));
        math221.addSection(aSection);
        aSection.setCourse(math221);

        Course phys203 = courseFactory.getCourse("PHYS", 203);
        aSection = new Section("201", "TR", "09:30", "10:50", new Building("Hennings", new LatLon(49.266400, -123.252047)));
        phys203.addSection(aSection);
        aSection.setCourse(phys203);

        Course crwr209 = courseFactory.getCourse("CRWR", 209);
        aSection = new Section("002", "TR", "12:30", "13:50", new Building("Geography", new LatLon(49.266039, -123.256129)));
        crwr209.addSection(aSection);
        aSection.setCourse(crwr209);

        Course fnh330 = courseFactory.getCourse("FNH", 330);
        aSection = new Section("002", "TR", "15:00", "16:20", new Building("MacMillian", new LatLon(49.261167, -123.251157)));
        fnh330.addSection(aSection);
        aSection.setCourse(fnh330);

        Course cpsc499 = courseFactory.getCourse("CPSC", 430);
        aSection = new Section("201", "TR", "16:20", "17:50", new Building("Liu", new LatLon(49.267632, -123.259334)));
        cpsc499.addSection(aSection);
        aSection.setCourse(cpsc499);

        Course chem250 = courseFactory.getCourse("CHEM", 250);
        aSection = new Section("203", "TR", "10:00", "11:20", new Building("Klinck", new LatLon(49.266112, -123.254776)));
        chem250.addSection(aSection);
        aSection.setCourse(chem250);

        Course eosc222 = courseFactory.getCourse("EOSC", 222);
        aSection = new Section("200", "TR", "11:00", "12:20", new Building("ESB", new LatLon(49.262866, -123.25323)));
        eosc222.addSection(aSection);
        aSection.setCourse(eosc222);

        Course biol201 = courseFactory.getCourse("BIOL", 201);
        aSection = new Section("201", "TR", "14:00", "15:20", new Building("BioSci", new LatLon(49.263920, -123.251552)));
        biol201.addSection(aSection);
        aSection.setCourse(biol201);
    }

    /**
     * This asynchronous task is responsible for contacting the Meetup web service
     * for the schedule of a random student. The task must plot the retrieved
     * student's route for the schedule on the map in a different colour than the "me" schedule
     * or must display a dialog box that a schedule was not retrieved.
     */
    private class GetRandomSchedule extends AsyncTask<Void, Void, SchedulePlot> {

        // Some overview explanation of asynchronous tasks is on the project web page.

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected SchedulePlot doInBackground(Void... params) {

            // CPSC 210 Students: You must complete this method. It needs to
            // contact the Meetup web service to get a random student's schedule.
            // If it is successful in retrieving a student and their schedule,
            // it needs to remember the student in the randomStudent field
            // and it needs to create and return a schedulePlot object with
            // all relevant information for being ready to retrieve the route
            // and plot the route for the schedule. If no random student is
            // retrieved, return null.
            //
            // Note, leave all determination of routing and plotting until
            // the onPostExecute method below.

            SchedulePlot randomSchedule = null;

            String returnString = null;
            try {
                returnString = makeRoutingCall(getStudentURL);
            } catch (IOException e) {
                e.printStackTrace();
            }
            randomStudent = studentFromJSON(returnString);
            //int randomID = randomStudent.getId();

            //studentManager.addStudent(randomStudent.getLastName(), randomStudent.getFirstName(), randomID);
            //enrolls randomStudent in each class via helper
            //addSections(studentManager, randomID, returnString);

            String randomName = randomStudent.getFirstName() + " " + randomStudent.getLastName();
            String dayOfWeek = sharedPreferences.getString("dayOfWeek", "MWF");
            CourseFactory factory = CourseFactory.getInstance();

            //adds the sections to randomStudent's schedule.
            TreeSet<Section> toAdd = new TreeSet<Section>();
            TreeSet<Section> mwfSections = getSections(factory, returnString, "MWF");
            TreeSet<Section> trSections = getSections(factory, returnString, "TR");

            toAdd.addAll(mwfSections);
            toAdd.addAll(trSections);

            for (Section s : toAdd) {
                randomStudent.getSchedule().add(s);
            }

            TreeSet<Section> randomSection = getSections(factory, returnString, dayOfWeek);
            randomSchedule = new SchedulePlot(randomSection, randomName, "#000000", R.drawable.ic_action_place);

            return randomSchedule;
        }

        /**
         * @param cf
         * @param returnString
         * @param dayOfWeek
         * @return
         */
        private TreeSet<Section> getSections(CourseFactory cf, String returnString, String dayOfWeek) {

            TreeSet<Section> toReturn = new TreeSet<Section>();

            JSONTokener token = new JSONTokener(returnString);
            try {
                JSONArray JSections = new JSONObject(token).getJSONArray("Sections");
                for (int i = 0; i < JSections.length(); i++) {
                    String courseName = JSections.getJSONObject(i).getString("CourseName");
                    int courseNumber = Integer.parseInt(JSections.getJSONObject(i).getString("CourseNumber"));
                    String sectionName = JSections.getJSONObject(i).getString("SectionName");

                    Section sectionToAdd = cf.getCourse(courseName, courseNumber).getSection(sectionName);

                    if (sectionToAdd.getDayOfWeek().equals(dayOfWeek)) {
                        toReturn.add(sectionToAdd);
                    }
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
            return toReturn;
        }

        /**
         * Helper JSONParser to doInBackground
         *
         * @param returnString is JSON from httpRequest
         * @return Student.
         */
        private Student studentFromJSON(String returnString) {
            Student toReturn = null;
            JSONTokener parsed = new JSONTokener(returnString);
            try {
                JSONObject JStudent = new JSONObject(parsed);
                String lastName = JStudent.getString("LastName");
                String firstName = JStudent.getString("FirstName");
                int id = Integer.parseInt(JStudent.getString("Id"));

                toReturn = new Student(lastName, firstName, id);


            } catch (JSONException e) {
                e.printStackTrace();
            }

            return toReturn;
        }

        /**
         * Helper Method to doInBackground.
         *
         * @param httpRequest web adress
         * @return JSON in string.
         * @throws MalformedURLException
         * @throws IOException
         */
        private String makeRoutingCall(String httpRequest) throws MalformedURLException, IOException {
            URL url = new URL(httpRequest);
            HttpURLConnection client = (HttpURLConnection) url.openConnection();
            InputStream in = client.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String returnString = br.readLine();
            client.disconnect();
            return returnString;
        }


        @Override
        protected void onPostExecute(SchedulePlot schedulePlot) {
            // CPSC 210 students: When this method is called, it will be passed
            // whatever schedulePlot object you created (if any) in doBackground
            // above. Use it to plot the route.
            new GetRoutingForSchedule().execute(schedulePlot);

        }
    }

    /**
     * This asynchronous task is responsible for contacting the MapQuest web service
     * to retrieve a route between the buildings on the schedule and for plotting any
     * determined route on the map.
     */
    private class GetRoutingForSchedule extends AsyncTask<SchedulePlot, Void, SchedulePlot> {

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected SchedulePlot doInBackground(SchedulePlot... params) {

            // The params[0] element contains the schedulePlot object
            SchedulePlot scheduleToPlot = params[0];

            List<GeoPoint> waypointsToAdd = new ArrayList<GeoPoint>();
            //scheduleToPlot.setRoute(waypointsToAdd);

//            String startingPoint = UBC_BUS_LOOP.getLatitude() + ","
//                    + UBC_BUS_LOOP.getLongitude();
            //Log.d("boo.", centrePoint);

            List<Section> sectionsToPlot = new ArrayList<Section>(scheduleToPlot.getSections());

            if (sectionsToPlot.size() == 1) {
                Double ClassLat = sectionsToPlot.get(0).getBuilding().getLatLon().getLatitude();
                Double ClassLng = sectionsToPlot.get(0).getBuilding().getLatLon().getLongitude();

                GeoPoint classToAdd = new GeoPoint(ClassLat, ClassLng);
                waypointsToAdd.add(classToAdd);
            } else {
                for (int i = 0; i < (sectionsToPlot.size() - 1); i++) {

                    String latLon1 = sectionsToPlot.get(i).getBuilding().getLatLon().toString();

                    String latLon2 = sectionsToPlot.get(i + 1).getBuilding().getLatLon().toString();
                    String httpRequest = "http://open.mapquestapi.com/directions/v2" +
                            "/route?key=" + MAP_QUEST_APP_KEY +
                            "&routeType=pedestrian" +
                            "&from="
                            + latLon1 +
                            "&to=" + latLon2;

                    try {
                        List<GeoPoint> returnList1 = pointsFromJSON(makeRoutingCall(httpRequest));

                        List<GeoPoint> returnedPoints = new ArrayList<GeoPoint>();
                        returnedPoints.addAll(returnList1);
                        for (GeoPoint g : returnedPoints) {

                            waypointsToAdd.add(g);

                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            scheduleToPlot.setRoute(waypointsToAdd);

            return scheduleToPlot;
        }

        /**
         * helper method to doInBackground
         *
         * @param JSONString, takes in the return String from calling the MapQuest webservice.
         * @return List<LatLon>, returns the list of LatLon parsed from the MapQuest webservice.
         */
        private List<GeoPoint> pointsFromJSON(String JSONString) {
            List<GeoPoint> pointsToAdd = new ArrayList<GeoPoint>();
            try {
                JSONTokener points = new JSONTokener(JSONString);
                JSONObject returnedPoints = new JSONObject(points);
                JSONArray legs = returnedPoints.getJSONObject("route").getJSONArray("legs");

                for (int i = 0; i < legs.length(); i++) {
                    JSONArray maneuvers = legs.getJSONObject(i).getJSONArray("maneuvers");

                    for (int j = 0; j < maneuvers.length(); j++) {
                        double lat = maneuvers.getJSONObject(j).getJSONObject("startPoint").getDouble("lat");
                        double lng = maneuvers.getJSONObject(j).getJSONObject("startPoint").getDouble("lng");
                        //Log.d("latLon is:", "" + lat + lng);
                        GeoPoint pointToAdd = new GeoPoint(lat, lng);
                        pointsToAdd.add(pointToAdd);
                    }

                }


            } catch (JSONException e) {
                e.printStackTrace();
            }

            return pointsToAdd;
        }

        /**
         * An example helper method to call a web service
         */
        private String makeRoutingCall(String httpRequest) throws MalformedURLException, IOException {
            URL url = new URL(httpRequest);
            HttpURLConnection client = (HttpURLConnection) url.openConnection();
            InputStream in = client.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String returnString = br.readLine();
            client.disconnect();
            return returnString;
        }

        @Override
        protected void onPostExecute(SchedulePlot schedulePlot) {

            // CPSC 210 Students: This method should plot the route onto the map
            // with the given line colour specified in schedulePlot. If there is
            // no route to plot, a dialog box should be displayed.

            // To actually make something show on the map, you can use overlays.
            // For instance, the following code should show a line on a map
            // To actually make something show on the map, you can use overlays.
            // For instance, the following code should show a line on a map
            // PathOverlay po = createPathOverlay("#FFFFFF");
            // po.addPoint(point1); // one end of line
            // po.addPoint(point2); // second end of line
            // scheduleOverlay.add(po);
            // OverlayManager om = mapView.getOverlayManager();
            // om.addAll(scheduleOverlay);
            // mapView.invalidate(); // cause map to redraw


            List<GeoPoint> waypoints = schedulePlot.getRoute();
            PathOverlay po = createPathOverlay(schedulePlot.getColourOfLine());
            po.clearPath();

            if (schedulePlot.getRoute().isEmpty()) {
                AlertDialog aDialog = createSimpleDialog(schedulePlot.getName() + " has no classes today!");
                aDialog.show();

            }
            if (schedulePlot.getRoute().size() == 1) {
                AlertDialog aDialog = createSimpleDialog(schedulePlot.getName() + " has  only one class today!");
                aDialog.show();

            }

//
            for (GeoPoint p : waypoints) {
                po.addPoint(p);
            }
            scheduleOverlay.add(po);
            OverlayManager om = mapView.getOverlayManager();
            om.addAll(scheduleOverlay);
            mapView.invalidate(); // cause map to redraw
            //plot the buildings for class
            plotBuildings(schedulePlot);


        }

    }

    /**
     * This asynchronous task is responsible for contacting the FourSquare web service
     * to retrieve all places around UBC that have to do with food. It should load
     * any determined places into PlaceFactory and then display a dialog box of how it did
     * `
     */
    private class GetPlaces extends AsyncTask<Void, Void, String> {

        protected String doInBackground(Void... params) {
            // CPSC 210 Students: Complete this method to retrieve a string
            // of JSON from FourSquare. Return the string from this method

            String httpRequest =
                    "https://api.foursquare.com/v2/venues/explore?ll=" +
                            UBC_BUS_LOOP.getLatitude() + ","
                            + UBC_BUS_LOOP.getLongitude() +//replace with something else later.
                            "&radius=2500&query=food&limit=100&client_id=" + FOUR_SQUARE_CLIENT_ID +
                            "&client_secret=" + FOUR_SQUARE_CLIENT_SECRET +
                            "&v=20130815";

            Log.d("webquery = ", httpRequest);
            try {
                String toReturn = makeRoutingCall(httpRequest);
                return toReturn;

            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }

        /**
         * helper method to call a web service
         */
        private String makeRoutingCall(String httpRequest) throws MalformedURLException, IOException {
            URL url = new URL(httpRequest);
            HttpURLConnection client = (HttpURLConnection) url.openConnection();
            InputStream in = client.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String returnString = br.readLine();
            client.disconnect();
            return returnString;
        }

        protected void onPostExecute(String jSONOfPlaces) {

            // CPSC 210 Students: Given JSON from FourQuest, parse it and load
            // PlaceFactory
            //showRandomStudentsSchedule();

            JSONTokener toParse = new JSONTokener(jSONOfPlaces);
            int length;
            Log.d("parsing string : ", jSONOfPlaces);
            try {
                JSONObject JPlaces = new JSONObject(toParse);
                JSONArray items = JPlaces.getJSONObject("response").
                        getJSONArray("groups").getJSONObject(0).getJSONArray("items");
                length = items.length();
                AlertDialog aDialog = createSimpleDialog("found "
                        + length + " places for meeting up around campus!");
                aDialog.show();


                for (int i = 0; i < length; i++) {
                    JSONObject venue = items.getJSONObject(i).getJSONObject("venue");
                    String name = venue.getString("name");
                    JSONObject location = venue.getJSONObject("location");
                    String address = location.getString("address");
                    Double lat = location.getDouble("lat");
                    Double lng = location.getDouble("lng");

                    LatLon vLatLon = new LatLon(lat, lng);

//                    Building venueBuilding = new Building(name, vLatLon);
//                    plotABuilding(venueBuilding, name,
//                            address, R.drawable.ic_action_event);
//                    mapView.invalidate();

                    EatingPlace venueToAdd = new EatingPlace(name, vLatLon);
                    venueToAdd.setDisPlayText(address);
                    PlaceFactory placeFactory = PlaceFactory.getInstance();
                    placeFactory.add(venueToAdd);
                }


            } catch (JSONException e) {
                e.printStackTrace();
            }


        }

    }

}
