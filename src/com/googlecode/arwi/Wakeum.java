/*
 * Copyright (C) 2008  Patrick F. Linehan
 * 
 * See the LICENSE file for details.
 */
package package com.googlecode.arwi;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;

public class Wakeum extends MapActivity
{
    public static final String TAG = "Wakeum";
    
    private static final int MENU_ITEM_CLEAR_MAP = 10000;
    private static final int MENU_ITEM_MAP_MODE = 10001;
    private static final int MENU_ITEM_MAP_MODE_SUB_MAP = 10002;
    private static final int MENU_ITEM_MAP_MODE_SUB_SATELLITE = 10003;
    private static final int MENU_ITEM_MAP_MODE_SUB_TRAFFIC = 10004;
    private static final int MENU_ITEM_ZOOM = 10005;
    private static final int MENU_ITEM_ADD_ALARM = 10006;

    private MapView mapView;
    private AlarmsOverlay alarmsOverlay;
    private MyLocationOverlay myLocationOverlay;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        this.mapView = (MapView)findViewById(R.id.mapview);

        LinearLayout zoomLayout = (LinearLayout)findViewById(R.id.mapview_zoom);
        zoomLayout.addView(
                        this.mapView.getZoomControls(),
                        new ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT));

        this.alarmsOverlay = new AlarmsOverlay(this);
        this.myLocationOverlay = new MyLocationOverlay(this, this.mapView);
        final MapView mapView = this.mapView;
        final MyLocationOverlay myLocationOverlay = this.myLocationOverlay;
        this.myLocationOverlay.runOnFirstFix(new Runnable() {
            public void run()
            {
                Log.e(TAG, "GOT FIRST FIX");
                mapView.getController().animateTo(myLocationOverlay.getMyLocation());
            }
        });

        this.mapView.getOverlays().add(this.alarmsOverlay);
        this.mapView.getOverlays().add(this.myLocationOverlay);
        this.mapView.postInvalidate();
    }

    @Override
    protected void onPause()
    {
        this.myLocationOverlay.disableMyLocation();
        super.onPause();
    }
    
    @Override
    protected void onResume()
    {
        super.onResume();
        Log.e(TAG, "onResume:enableMyLocation: " + this.myLocationOverlay.enableMyLocation());
    }
    
    @Override
    protected boolean isRouteDisplayed()
    {
        return false;
    }

    /**
     * Adds some basic menu items. The order is Menu.CATEGORY_SECONDARY so
     * additional items can be placed before these items.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);

        MenuItem menuItem = null;
        SubMenu subMenu = null;

        menuItem =
                        menu.add(
                                        Menu.NONE,
                                        MENU_ITEM_CLEAR_MAP,
                                        Menu.CATEGORY_SECONDARY,
                                        "Clear map");
        menuItem.setAlphabeticShortcut('c');
        menuItem.setIcon(android.R.drawable.ic_menu_revert);

        subMenu =
                        menu.addSubMenu(
                                        Menu.NONE,
                                        MENU_ITEM_MAP_MODE,
                                        Menu.CATEGORY_SECONDARY,
                                        "Map mode");
        subMenu.setIcon(android.R.drawable.ic_menu_mapmode);

        menuItem = subMenu.add(0, MENU_ITEM_MAP_MODE_SUB_MAP, Menu.NONE, "Map");
        menuItem.setAlphabeticShortcut('m');
        menuItem.setCheckable(true);
        menuItem.setChecked(false);

        menuItem =
                        subMenu.add(
                                        0,
                                        MENU_ITEM_MAP_MODE_SUB_SATELLITE,
                                        Menu.NONE,
                                        "Satellite");
        menuItem.setAlphabeticShortcut('s');
        menuItem.setCheckable(true);
        menuItem.setChecked(false);

        menuItem =
                        subMenu.add(
                                        0,
                                        MENU_ITEM_MAP_MODE_SUB_TRAFFIC,
                                        Menu.NONE,
                                        "Traffic");
        menuItem.setAlphabeticShortcut('t');
        menuItem.setCheckable(true);
        menuItem.setChecked(false);

        menuItem = menu.add(0, MENU_ITEM_ZOOM, Menu.CATEGORY_SECONDARY, "Zoom");
        menuItem.setAlphabeticShortcut('z');
        menuItem.setIcon(android.R.drawable.ic_menu_zoom);

        menuItem =
                        menu.add(
                                        0,
                                        MENU_ITEM_ADD_ALARM,
                                        Menu.CATEGORY_SECONDARY,
                                        "Add Alarm");
        menuItem.setAlphabeticShortcut('a');
        menuItem.setIcon(android.R.drawable.ic_menu_add);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case MENU_ITEM_CLEAR_MAP:
                this.mapView.setSatellite(false);
                this.mapView.setTraffic(false);
                this.alarmsOverlay.deleteAll();
                return true;

            case MENU_ITEM_MAP_MODE_SUB_MAP:
                this.mapView.setSatellite(false);
                return true;

            case MENU_ITEM_MAP_MODE_SUB_SATELLITE:
                this.mapView.setSatellite(true);
                return true;

            case MENU_ITEM_MAP_MODE_SUB_TRAFFIC:
                this.mapView.setTraffic(!this.mapView.isTraffic());
                return true;

            case MENU_ITEM_ZOOM:
                this.mapView.displayZoomControls(true);
                return true;

            case MENU_ITEM_ADD_ALARM:
                GeoPoint mapCenter = this.mapView.getMapCenter();
                this.alarmsOverlay.create(mapCenter);
                this.mapView.postInvalidate();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        super.onPrepareOptionsMenu(menu);

        boolean isSatellite = this.mapView.isSatellite();
        boolean isTraffic = this.mapView.isTraffic();
        int numAlarms = this.alarmsOverlay.size();

        menu.findItem(MENU_ITEM_CLEAR_MAP).setEnabled(isSatellite || isTraffic || numAlarms > 0);
        menu
                        .findItem(MENU_ITEM_MAP_MODE_SUB_MAP)
                        .setChecked(!isSatellite)
                        .setEnabled(isSatellite);
        menu
                        .findItem(MENU_ITEM_MAP_MODE_SUB_SATELLITE)
                        .setChecked(isSatellite)
                        .setEnabled(!isSatellite);
        menu.findItem(MENU_ITEM_MAP_MODE_SUB_TRAFFIC).setChecked(isTraffic);

        return true;
    }
}
