package com.patidar.pawan.routeapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.GeoDataClient;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class MapsActivity extends FragmentActivity implements
        OnMapReadyCallback,GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private GoogleMap mMap;
    private static final int LOCATION_REQUEST=500;
    private GoogleApiClient googleApiClient;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationRequest locationRequest;
    private Location lastLocation;
    private GeoDataClient mGeoDataClient;
    private Marker currentUserLocationMarker,destinationMarker;
    private AutoCompleteTextView current_location,destination;
    private PlaceAutocompleteAdapter placeAutocompleteAdapter;
    private ImageView gps_image;
    private List<LatLng> possition = new ArrayList<>();
    private static final float ZOOM= 18;
    private static final LatLngBounds LAT_LNG_BOUNDS= new LatLngBounds(
            new LatLng(-40,-168),new LatLng(71,136)
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        current_location = findViewById(R.id.input_source);
        destination = findViewById(R.id.input_destination);
        gps_image = findViewById(R.id.gps);
        mGeoDataClient = Places.getGeoDataClient(this, null);
        if(Build.VERSION.SDK_INT>Build.VERSION_CODES.M){
            isPermissionGranted();
            getDeviceLocation();
        }

        init();
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

    }

    private void getDeviceLocation(){
        fusedLocationProviderClient= LocationServices.getFusedLocationProviderClient(this);
        try{
            Task location = fusedLocationProviderClient.getLastLocation();
            location.addOnCompleteListener(new OnCompleteListener() {
                @Override
                public void onComplete(@NonNull Task task) {
                    if(task.isSuccessful()){
                        if(currentUserLocationMarker!=null){
                            currentUserLocationMarker.remove();
                        }
                        Location currentLocation= (Location) task.getResult();
                        LatLng latLng= new LatLng(currentLocation.getLatitude(),currentLocation.getLongitude());
                        moveCamera(latLng,ZOOM);
                        MarkerOptions markerOptions= new MarkerOptions();
                        markerOptions.position(latLng);
                        if(possition.size()>0){
                            possition.remove(0);
                        }
                        possition.add(0,latLng);
                        markerOptions.title("user current Location");
                        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                        currentUserLocationMarker = mMap.addMarker(markerOptions);
                        String cityname= getCurrentCity(currentLocation.getLatitude(),currentLocation.getLongitude());
                        current_location.setText(cityname);
                    }else{

                    }
                }
            });

        }catch (SecurityException e){
            Log.e("Security",""+e.getMessage());
        }
    }

    private void moveCamera(LatLng latlng, Float zoom){
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latlng,zoom));

    }

    private void init(){
        placeAutocompleteAdapter = new PlaceAutocompleteAdapter(this,mGeoDataClient,LAT_LNG_BOUNDS,null);

        destination.setAdapter(placeAutocompleteAdapter);
        destination.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if(actionId == EditorInfo.IME_ACTION_SEARCH
                        || actionId ==EditorInfo.IME_ACTION_DONE
                        || event.getAction() == KeyEvent.ACTION_DOWN
                        || event.getAction() == KeyEvent.KEYCODE_ENTER){
                    Log.e("Location_serch","Done");
                    hideSoftkeyboard();
                    geoLocate();
                }
                return false;
            }
        });
        gps_image.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getDeviceLocation();
            }
        });

    }

    private String getCurrentCity(Double lat,double lon){
        String city="";
        Geocoder geocoder = new Geocoder(MapsActivity.this, Locale.getDefault());
        List<Address> list = new ArrayList<>();
        try{
            list = geocoder.getFromLocation(lat,lon,1);
            if(list.size()>0) {
                for(Address address:list){
                    if(address.getAddressLine(0)!=null && address.getAddressLine(0).length()>0){
                        Log.e("LocationAdress",""+address);
                        city=address.getAddressLine(0);
                        break;
                    }
                }

            }
        }catch (Exception e){
            Log.e("IOEXception " ,"Geolocaion Exeption ");
        }


        return city;
    }

    private void geoLocate(){

        String search_string = destination.getText().toString();
        Geocoder geocoder = new Geocoder(MapsActivity.this);
        List<Address> list = new ArrayList<>();
         try{
                 list = geocoder.getFromLocationName(search_string,1);
         }catch (Exception e){
             Log.e("IOEXception " ,"Geolocaion Exeption ");
         }

         if(list.size()>0){
             if(destinationMarker!=null){
                 destinationMarker.remove();
             }
             Address address= list.get(0);
             LatLng latLng  = new LatLng(address.getLatitude(),address.getLongitude());
             MarkerOptions markerOptions= new MarkerOptions();
             markerOptions.position(latLng);
             if(possition.size()==2){
                 possition.remove(1);
             }
             possition.add(1,latLng);
             markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
             destinationMarker = mMap.addMarker(markerOptions);
             moveCamera(latLng,12f);
             drawRoute();
         }
        hideSoftkeyboard();
    }

    private void drawRoute(){
        if(possition.size()==2){
            String url = getRequesturl(possition.get(0),possition.get(1));
            TaskRequestDirection taskRequestDirection = new TaskRequestDirection();
            taskRequestDirection.execute(url);
        }
    }

    private String getRequesturl(LatLng origin  , LatLng endpoint)
    {
        String str_org = "origin="+ origin.latitude+","+origin.longitude;
        String str_des  = "destination="+ endpoint.latitude+","+endpoint.longitude;
        String sensor = "sensor=false";
        String mode = "mode= driving";
        String param=  str_org +"&"+str_des+"&"+sensor+"&"+mode;
        String  output= "json";
        String url="https://maps.googleapis.com/maps/api/directions/"+output+"?"+param + "&key=AIzaSyBZ6lewZheWz8CD5PtH6Jr1LopIUgL-eJk";
        return url;
    }

    private String requestDirectio(String urlstr) throws IOException {
        String responseString="";
        InputStream inputStream=null;
        HttpURLConnection httpURLConnection = null;
        try{
            URL url= new URL(urlstr);
            httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.connect();
            inputStream= httpURLConnection.getInputStream();
            InputStreamReader inputStreamReader= new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            StringBuffer stringBuffer= new StringBuffer();
            String  line ="";
            while ((line= bufferedReader.readLine() )!=null){
                stringBuffer.append(line);
            }

            responseString = stringBuffer.toString();
            bufferedReader.close();;
            inputStreamReader.close();


        }catch (Exception e){
            e.printStackTrace();
        }finally {
            if(inputStream!=null){
                inputStream.close();
            }
            httpURLConnection.disconnect();
        }
        return responseString;
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ) {
            buildGoogleApiClient();
            return;
        }
        mMap.setMyLocationEnabled(true);
    }

    private boolean isPermissionGranted(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.ACCESS_FINE_LOCATION)){
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST);
            }else{
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST);
            }
            return false;
        }else{
            return true;
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case LOCATION_REQUEST:
                if(grantResults.length>0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        if(googleApiClient ==null){
                            buildGoogleApiClient();
                        }
                        mMap.setMyLocationEnabled(true);
                    }
                }
                break;
        }
    }

    protected synchronized void buildGoogleApiClient(){
        googleApiClient= new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        googleApiClient.connect();
    }

    @Override
    public void onLocationChanged(Location location) {
        lastLocation=location;

        getDeviceLocation();
        if ( googleApiClient != null){
            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient,this);
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(1100);
        locationRequest.setFastestInterval(1100);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient,locationRequest,this);
        }

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
    private void hideSoftkeyboard(){
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    public class TaskRequestDirection extends AsyncTask<String,Void, String >{

        @Override
        protected String doInBackground(String... strings) {
          String responseString ="";
          try{
              responseString = requestDirectio(strings[0]);
          }catch (Exception e){
              e.printStackTrace();
          }
            return responseString;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            Log.e("DataPAerse",""+s);
            TaskParser taskParser = new TaskParser();
            taskParser.execute(s);
        }
    }

    public class TaskParser extends AsyncTask<String ,Void,List<List<HashMap<String,String>>>>{

        @Override
        protected List<List<HashMap<String, String>>> doInBackground(String... strings) {
            JSONObject jsonObject= null;
            List<List<HashMap<String,String>>> routes=null;
            try {

                jsonObject= new JSONObject(strings[0]);
                DirectionsJSONParser directionsJSONParser = new DirectionsJSONParser();
                routes = directionsJSONParser.parse(jsonObject);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return routes;
        }

        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> lists) {
            ArrayList points = null;

            PolylineOptions polylineOptions =null;

            for (List<HashMap<String,String>> path: lists){
                points = new ArrayList();
                polylineOptions = new PolylineOptions();

                for (HashMap<String,String> point: path){
                    double lat = Double.parseDouble(point.get("lat"));
                    double lon = Double.parseDouble(point.get("lon"));
                    points.add(new LatLng(lat,lon));
                }
                polylineOptions.addAll(points);
                polylineOptions.width(15);
                polylineOptions.color(Color.BLUE);
                polylineOptions.geodesic(true);

            }
            if(polylineOptions !=null){
                mMap.addPolyline(polylineOptions);
            }else{
                Toast.makeText(MapsActivity.this, "Direction Not Found", Toast.LENGTH_SHORT).show();
            }
        }
    }

}
