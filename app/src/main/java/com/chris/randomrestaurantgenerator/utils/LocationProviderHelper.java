/*
 * Copyright (C) 2017 Chris Tarazi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.chris.randomrestaurantgenerator.utils;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.IntentSender;
import android.location.Location;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.arlib.floatingsearchview.FloatingSearchView;
import com.chris.randomrestaurantgenerator.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.util.List;

import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

/**
 * A helper class to aid with keeping one single instance of the Location object and all the other
 * periphery classes that go along with it.
 */
public class LocationProviderHelper implements LocationListener {

    public static final int REQUEST_CHECK_SETTINGS = 1;
    private static final int RC_LOCATION_PERM = 120;
    private static final String[] PERMISSIONS = new String[]{ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION};
    public static boolean useGPS = false;
    private Activity activity;
    private GoogleApiClient mGoogleClient;
    private LocationRequest mLocationRequest;
    private LocationSettingsRequest mLocationSettingsRequest;
    private Location mCurrentLocation;
    private FloatingSearchView searchLocationBox;
    private ProgressDialog progressDialog;

    public LocationProviderHelper(final Activity act, FloatingSearchView infoBox,
                                  GoogleApiClient googleApiClient) {
        this.activity = act;
        this.searchLocationBox = infoBox;
        this.mGoogleClient = googleApiClient;

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(2000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        builder.setAlwaysShow(true);
        mLocationSettingsRequest = builder.build();

        progressDialog = new ProgressDialog(this.activity);

        progressDialog.setMessage(this.activity.getString(R.string.string_getting_location));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        progressDialog.setButton(ProgressDialog.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                dismissLocationUpdater();
                useGPS = false;
            }
        });
    }

    public Location getLocation() {
        return mCurrentLocation;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location.hasAccuracy() && location.getAccuracy() < 200.0) {
            mCurrentLocation = location;
            progressDialog.dismiss();
            Log.d("RRG", "onLocationChanged: " + location.getAccuracy() + "m");
            Toast.makeText(activity, R.string.string_location_acquired, Toast.LENGTH_LONG).show();
            searchLocationBox.setSearchText(activity.getString(R.string.string_current_location));
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleClient, this);
        }
    }

    // Called by EasyPermissions from MainActivityFragment
    public void onPermissionsGranted(int requestCode, List<String> perms) {
        Log.d("RRG", "onPermissionsGranted:" + requestCode + ":" + perms.size());
        requestLocation();
    }

    // Called by EasyPermissions from MainActivityFragment
    public void onPermissionsDenied(int requestCode, List<String> perms) {
        Log.d("RRG", "onPermissionsDenied:" + requestCode + ":" + perms.size());

        // (Optional) Check whether the user denied any permissions and checked "NEVER ASK AGAIN."
        // This will display a dialog directing them to enable the permission in app settings.
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            new AppSettingsDialog.Builder(activity, activity.getResources().getString(R.string.string_location_perm_rationale))
                    .setTitle(activity.getResources().getString(R.string.title_settings_dialog))
                    .setPositiveButton(activity.getResources().getString(R.string.settings))
                    .setNegativeButton(activity.getResources().getString(R.string.cancel), null /* click listener */)
                    .setRequestCode(AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE)
                    .build()
                    .show();
        }
    }

    // Requests location updates from the FusedLocationApi.
    public void startLocationUpdates() {
        LocationServices.SettingsApi.checkLocationSettings(
                mGoogleClient,
                mLocationSettingsRequest
        ).setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
                final Status status = locationSettingsResult.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        requestLocation();
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        try {
                            // Show the dialog by calling startResolutionForResult(), and check the
                            // result in onActivityResult().
                            status.startResolutionForResult(
                                    activity,
                                    REQUEST_CHECK_SETTINGS);
                        } catch (IntentSender.SendIntentException ignored) {}
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        Toast.makeText(activity, R.string.string_location_settings_inadequate,
                                Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void stopLocationUpdates() {
        if (mGoogleClient.isConnected())
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleClient, this);
    }

    private void requestLocation() {
        // Check Google Play Services before requesting location.
        if (!mGoogleClient.isConnected()) {
            GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
            int resultCode = apiAvailability.isGooglePlayServicesAvailable(activity);
            if (resultCode != ConnectionResult.SUCCESS) {
                if (apiAvailability.isUserResolvableError(resultCode)) {
                    apiAvailability.getErrorDialog(activity, resultCode, 9000)
                            .show();
                } else {
                    activity.finish();
                }
            }
        } else {
            if (EasyPermissions.hasPermissions(activity, PERMISSIONS)) {
                progressDialog.show();
                try {
                    useGPS = true;
                    Location ll = LocationServices.FusedLocationApi.getLastLocation(mGoogleClient);
                    if (ll == null) {
                        LocationServices.FusedLocationApi.requestLocationUpdates(
                                mGoogleClient, mLocationRequest, this);
                    } else {
                        onLocationChanged(ll);
                    }
                } catch (SecurityException ignored) {
                    // Ignoring exception because this is handled already by EasyPermissions.
                    useGPS = false;
                }
            } else {
                EasyPermissions.requestPermissions(activity,
                        activity.getString(R.string.string_location_permission_required),
                        RC_LOCATION_PERM, PERMISSIONS);
            }
        }
    }

    public void pauseAndSaveLocationUpdates() {
        progressDialog.dismiss();
        stopLocationUpdates();
    }

    public void dismissLocationUpdater() {
        mCurrentLocation = null;
        progressDialog.dismiss();
        stopLocationUpdates();
        searchLocationBox.clearQuery();
    }
}
