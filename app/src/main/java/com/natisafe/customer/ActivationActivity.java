package com.natisafe.customer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class ActivationActivity extends AppCompatActivity {

    // Supabase
    private static final String SUPA_URL =
        "https://cxtrrejclkhqhkqbicmz.supabase.co";
    private static final String SUPA_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
        "eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImN4dHJyZWpjbGtocWhrcWJpY216Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk5OTk3MDksImV4cCI6MjA5NTU3NTcwOX0." +
        "_7wB4YwrYEnK6hWuR6YqcFxRb05OLnWvOelIC-ahIEQ";

    private EditText codeField;
    private TextView errorLabel;
    private Button submitBtn;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activation);

        codeField  = findViewById(R.id.act_code);
        errorLabel = findViewById(R.id.act_error);
        submitBtn  = findViewById(R.id.act_submit);
        progress   = findViewById(R.id.act_progress);

        // Auto-uppercase as user types and clear errors
        codeField.addTextChangedListener(new TextWatcher() {
            boolean editing = false;
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void onTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void afterTextChanged(Editable s) {
                if (editing) return;
                String t = s.toString();
                String up = t.toUpperCase().replaceAll("[^A-Z0-9]","");
                if (!up.equals(t)) {
                    editing = true;
                    codeField.setText(up);
                    codeField.setSelection(up.length());
                    editing = false;
                }
                errorLabel.setVisibility(View.GONE);
            }
        });

        submitBtn.setOnClickListener(v -> attemptActivation());
    }

    private void attemptActivation() {
        String code = codeField.getText().toString().trim().toUpperCase();
        if (code.length() < 4) {
            showError("הזן קוד הפעלה תקין");
            return;
        }
        setBusy(true);

        new Thread(() -> {
            try {
                String url = SUPA_URL + "/rest/v1/companies?activation_code=eq."
                    + URLEncoder.encode(code, "UTF-8")
                    + "&select=company_id,company_name";

                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestProperty("apikey", SUPA_KEY);
                conn.setRequestProperty("Authorization", "Bearer " + SUPA_KEY);
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);

                int rc = conn.getResponseCode();
                if (rc < 200 || rc >= 300) {
                    finishUI(false, "שגיאת שרת. נסה שוב.");
                    return;
                }
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONArray arr = new JSONArray(sb.toString());
                if (arr.length() == 0) {
                    finishUI(false, "קוד ההפעלה לא נמצא. בדוק שהקלדת נכון.");
                    return;
                }
                JSONObject co = arr.getJSONObject(0);
                String companyId = co.getString("company_id");

                // Save permanently
                SharedPreferences prefs = getSharedPreferences("nati_prefs", MODE_PRIVATE);
                prefs.edit()
                    .putString("company_id", companyId)
                    .putString("activation_code", code)
                    .apply();

                // Go to main
                runOnUiThread(() -> {
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                });

            } catch (java.net.UnknownHostException ue) {
                finishUI(false, "אין חיבור לאינטרנט. בדוק והקש שוב.");
            } catch (java.net.SocketTimeoutException te) {
                finishUI(false, "החיבור איטי מדי. נסה שוב.");
            } catch (Exception e) {
                finishUI(false, "שגיאה: " + e.getMessage());
            }
        }).start();
    }

    private void finishUI(boolean ok, String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            setBusy(false);
            if (!ok) showError(msg);
        });
    }

    private void setBusy(boolean busy) {
        submitBtn.setEnabled(!busy);
        submitBtn.setText(busy ? "מאמת..." : "אישור");
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisibility(View.VISIBLE);
    }
}
