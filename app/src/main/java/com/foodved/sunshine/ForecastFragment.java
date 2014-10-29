package com.foodved.sunshine;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.Logger;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment {

    private ArrayAdapter<String> mForecastAdapter;

    public ForecastFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        String[] forecastDataItems = {
                "Today - Sunny 88/62",
                "Tomorrow - Cloudy 88/65",
                "Sat - Cloudy 88/65",
                "Sun - Cloudy 88/65",
                "Mon - Cloudy 88/65",
                "Tue - Cloudy 88/65",
                "Wed - Cloudy 88/65"
        };

        ArrayList<String> weeklyList = new ArrayList<String>(Arrays.asList(forecastDataItems));

        mForecastAdapter = new ArrayAdapter<String>(
                getActivity(),
                R.layout.list_item_forecast,
                R.id.list_item_forecast_textview,
                weeklyList
        );

        FetchWeatherTask fetchWeatherTask = new FetchWeatherTask();
        fetchWeatherTask.execute("94043");

        ListView myListView = (ListView) rootView.findViewById(R.id.list_item_forecast);

        myListView.setAdapter(mForecastAdapter);

        return rootView;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            FetchWeatherTask fetchWeatherTask = new FetchWeatherTask();
            fetchWeatherTask.execute("94043");
            String result = fetchWeatherTask.forecastJsonStr;
        }
        return super.onOptionsItemSelected(item);
    }

     public class FetchWeatherTask extends AsyncTask<String, Void, String[]>
        {

            HttpURLConnection connection = null;
            BufferedReader reader     = null;
            String forecastJsonStr    = null;

            private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

            /* The date/time conversion code is going to be moved outside the asynctask later,
 * so for convenience we're breaking it out into its own method now.
 */
            private String getReadableDateString(long time){
                // Because the API returns a unix timestamp (measured in seconds),
                // it must be converted to milliseconds in order to be converted to valid date.
                Date date = new Date(time * 1000);
                SimpleDateFormat format = new SimpleDateFormat("E, MMM d");
                return format.format(date).toString();
            }

            /**
             * Prepare the weather high/lows for presentation.
             */
            private String formatHighLows(double high, double low) {
                // For presentation, assume the user doesn't care about tenths of a degree.
                long roundedHigh = Math.round(high);
                long roundedLow = Math.round(low);

                String highLowStr = roundedHigh + "/" + roundedLow;
                return highLowStr;
            }

            /**
             * Take the String representing the complete forecast in JSON Format and
             * pull out the data we need to construct the Strings needed for the wireframes.
             *
             * Fortunately parsing is easy:  constructor takes the JSON string and converts it
             * into an Object hierarchy for us.
             */
            private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
                    throws JSONException {

                // These are the names of the JSON objects that need to be extracted.
                final String OWM_LIST = "list";
                final String OWM_WEATHER = "weather";
                final String OWM_TEMPERATURE = "temp";
                final String OWM_MAX = "max";
                final String OWM_MIN = "min";
                final String OWM_DATETIME = "dt";
                final String OWM_DESCRIPTION = "main";

                JSONObject forecastJson = new JSONObject(forecastJsonStr);
                JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

                String[] resultStrs = new String[numDays];
                for(int i = 0; i < weatherArray.length(); i++) {
                    // For now, using the format "Day, description, hi/low"
                    String day;
                    String description;
                    String highAndLow;

                    // Get the JSON object representing the day
                    JSONObject dayForecast = weatherArray.getJSONObject(i);

                    // The date/time is returned as a long.  We need to convert that
                    // into something human-readable, since most people won't read "1400356800" as
                    // "this saturday".
                    long dateTime = dayForecast.getLong(OWM_DATETIME);
                    day = getReadableDateString(dateTime);

                    // description is in a child array called "weather", which is 1 element long.
                    JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                    description = weatherObject.getString(OWM_DESCRIPTION);

                    // Temperatures are in a child object called "temp".  Try not to name variables
                    // "temp" when working with temperature.  It confuses everybody.
                    JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                    double high = temperatureObject.getDouble(OWM_MAX);
                    double low = temperatureObject.getDouble(OWM_MIN);

                    highAndLow = formatHighLows(high, low);
                    resultStrs[i] = day + " - " + description + " - " + highAndLow;
                }

                return resultStrs;
            }

            @Override
            protected String[] doInBackground(String... params) {
                if (params.length == 0)
                    return null;

                String mode = "json";
                String units = "metric";
                int numOfDays = 7;

                try {

                    final String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily";
                    final String QUERY_PARAM = "q";
                    final String FORMAT_PARAM = "mode";
                    final String UNITS_PARAM = "units";
                    final String DAYS_PARAM = "cnt";


                    Uri builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                            .appendQueryParameter(QUERY_PARAM, params[0])
                            .appendQueryParameter(FORMAT_PARAM, mode)
                            .appendQueryParameter(UNITS_PARAM, units)
                            .appendQueryParameter(DAYS_PARAM, Integer.toString(numOfDays))
                            .build();

                    URL url = new URL(builtUri.toString());

                    //Log.v(LOG_TAG, "Built URI:  " + builtUri.toString());

                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.connect();

                    InputStream inputStream = connection.getInputStream();
                    StringBuilder builder = new StringBuilder();
                    reader = new BufferedReader(new InputStreamReader(inputStream));

                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line + "\n");
                    }

                    if (builder == null || builder.length() == 0)
                        return null;

                    forecastJsonStr = builder.toString();
                    //Log.v(LOG_TAG, "Forecast JSON string: " + forecastJsonStr);

                    try {
                        return getWeatherDataFromJson(forecastJsonStr, numOfDays);
                    }
                    catch (JSONException jsoe){
                        Log.e(LOG_TAG, "JSON exception getting weather data " + jsoe.getMessage());
                    }

                } catch (MalformedURLException mfe) {
                    Log.e(LOG_TAG, "Error ", mfe);
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Error ", e);

                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (final IOException e) {
                            Log.e("PlaceholderFragment", "Error closing stream", e);
                        }
                    }
                }
                return null;//forecastJsonStr;
            }

            @Override
            protected void onPostExecute (String[] result) {
                try {
                    mForecastAdapter.clear();

                    for (String dayForecastStr : result)
                        mForecastAdapter.add(dayForecastStr);
                }
                catch (Exception e){
                    Log.e(LOG_TAG, e.getMessage());
                }
            }
        }
}