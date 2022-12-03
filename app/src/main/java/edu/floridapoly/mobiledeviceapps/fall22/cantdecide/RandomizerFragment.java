package edu.floridapoly.mobiledeviceapps.fall22.cantdecide;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.BreakIterator;
import java.util.Locale;
import java.util.Random;

public class RandomizerFragment extends Fragment {

    View rootView;
    Button acceptButton, declineButton, websiteButton;
    TextView charityTitle, charityDescription;
    ImageView charityLogo;
    Handler handler;

    String apiKey = "62c0b716a7f2b4e7ed7a47b062545cbf";
    String name, mission, websiteURL, logoURL, ID;
    boolean allowDuplicates, saveCharities;

    DBHelper dbHelper;
    Charity randomCharity;
    SharedPreferences sharedPreferences;


    public RandomizerFragment() {/* Required empty public constructor */}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler();
        dbHelper = new DBHelper(getContext());
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        allowDuplicates = sharedPreferences.getBoolean("Enable Duplicates", false);
        saveCharities = sharedPreferences.getBoolean("Save Charities", true);
        startRandomizer();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        rootView = inflater.inflate(R.layout.fragment_randomizer, container, false);

        acceptButton = rootView.findViewById(R.id.ApproveButton);
        declineButton = rootView.findViewById(R.id.DeclineButton);
        websiteButton = rootView.findViewById(R.id.WebsiteButton);
        charityTitle = rootView.findViewById(R.id.CharityName);
        charityDescription = rootView.findViewById(R.id.CharityDesc);
        charityLogo = rootView.findViewById(R.id.CharityLogo);
        charityLogo.setImageBitmap(null);

        acceptButton.setOnClickListener(view -> {
            if (saveCharities) {
                randomCharity = new Charity(ID, name, mission, websiteURL);
                dbHelper.insertCharity(randomCharity);
                Toast.makeText(getContext(), "Charity accepted, added to rolled charity listing", Toast.LENGTH_SHORT).show();
            }
            else
                Toast.makeText(getContext(), "Charity saving disabled, please enable in settings!", Toast.LENGTH_SHORT).show();
            startRandomizer();
        });
        declineButton.setOnClickListener(view -> {
            Toast.makeText(getContext(), "Charity declined, rolling next charity", Toast.LENGTH_SHORT).show();
            startRandomizer();
        });
        websiteButton.setOnClickListener(view -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(websiteURL));
            startActivity(browserIntent);
        });


        return rootView;
    }

    protected void startRandomizer() {
        String regionFilter = sharedPreferences.getString("Region Filter", null);
        String causeFilter = sharedPreferences.getString("Cause Filter", null);
        String query = "https://api.pledge.to/v1/organizations?q="
                + ((regionFilter != null) ? "&region=" + regionFilter : "")
                + ((causeFilter != null) ? "&cause=" + causeFilter : "");

        new getWebServiceData().execute(query);

    }

    class getWebServiceData extends AsyncTask {

        ProgressDialog progressDialog;
        URL url;
        Bitmap bitmap;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(getContext());
            progressDialog.setMessage("Generating Random Charity");
            progressDialog.setCancelable(false);
            progressDialog.show();

        }

        @Override
        protected Object doInBackground(Object[] objects) {
            return getPledgeAPIResponseData((String) objects[0]);
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);
            if(progressDialog.isShowing())
                progressDialog.dismiss();
            charityTitle.setText(name);
            BreakIterator breakIterator = BreakIterator.getSentenceInstance(Locale.US);
            breakIterator.setText(mission);
            charityDescription.setText(mission.substring(breakIterator.first(), breakIterator.next()));
            charityLogo.setImageBitmap(bitmap);
        }

        protected Void getPledgeAPIResponseData(String path) {
            StringBuffer response = new StringBuffer();
            String responseText;

            sendHttpRequest(path, response);
            responseText = response.toString();
            parseRandomCharity(path, response, responseText);

            return null;
        }

        private void parseRandomCharity(String path, StringBuffer response, String responseText) {
            try {
                JSONObject targetCharity = getRandomCharity(path, response, responseText);
                ID = targetCharity.getString("id");
                if (!allowDuplicates) {
                    while (dbHelper.isDuplicate(ID)) {
                        targetCharity = getRandomCharity(path, response, responseText);
                        ID = targetCharity.getString("id");
                    }
                }

                name = targetCharity.getString("name");
                mission = targetCharity.getString("mission");
                websiteURL = targetCharity.getString("website_url");
                logoURL = targetCharity.getString("logo_url");

                // Turn the http url from the charity JSON into a viewable image
                InputStream inputStream = new URL(logoURL).openStream();
                bitmap = BitmapFactory.decodeStream(inputStream);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private JSONObject getRandomCharity(String path, StringBuffer response, String responseText) throws JSONException {
            int totalResults, generatedPos, targetPage, resultIndex, perPage;
            JSONObject results = new JSONObject(responseText);
            totalResults = Math.min(results.getInt("total_count"), 1000);
            perPage = results.getInt("per");
            generatedPos = (new Random()).nextInt(totalResults);
            targetPage = (int) Math.ceil(generatedPos/(double) perPage);
            if (targetPage != 1) {
                // Generate new api response to go to appropriate page
                response.delete(0, response.length());
                String newPath = path + "&page=" + targetPage;
                sendHttpRequest(newPath, response);
                responseText = response.toString();
                results = new JSONObject(responseText);
            }
            // Find the position of the random charity
            resultIndex = (generatedPos % (perPage-1));
            // Fetch the random charity data from the initial JSONObject
            JSONArray resultsJSONArray = results.getJSONArray("results");
            return resultsJSONArray.getJSONObject(resultIndex);
        }

        private void sendHttpRequest(String path, StringBuffer response) {
            try {
                url = new URL(path);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(5000);
                conn.setConnectTimeout(5000);
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestMethod("GET");

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    String output;

                    while ((output = in.readLine()) != null) {
                        response.append(output);
                    }
                    in.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}