/*
 * Copyright (C) 2008  Patrick F. Linehan
 * 
 * See the LICENSE file for details.
 */
package com.googlecode.arwi;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import junit.framework.Assert;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Paint.Style;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapView;
import com.google.android.maps.Projection;

class AlarmsOverlay extends ItemizedOverlay<AlarmItem>
{
	/**
	 * Amount of room to shift dragging images for finger height.
	 */
	private static final int FINGER_Y_OFFSET = -45;
	
    private final Arwi arwi;
    private final DbHelper dbHelper;
    private final Paint fillPaint;
    private final Paint strokePaint;
    private final Paint dottedStrokePaint;
    private Alarm[] alarms;
    
    private float miniCircleRadius = 3;
    private float currCircleRadius = 50;
    private final int strokeWidth = 5;

    public AlarmsOverlay(Arwi arwi)
    {
        super(boundCenterBottom(arwi.getResources().getDrawable(R.drawable.pin_red)));
        //super.setDrawFocusedItem(false);
        
        this.arwi = arwi;
        this.dbHelper = new DbHelper(arwi);

        this.fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.fillPaint.setARGB(64, 255, 119, 107);
        
        this.strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.strokePaint.setARGB(255, 255, 119, 107);
        this.strokePaint.setStyle(Style.STROKE);
        this.strokePaint.setStrokeWidth(strokeWidth);

        this.dottedStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.dottedStrokePaint.setARGB(255, 148, 154, 170);
        this.dottedStrokePaint.setStyle(Style.STROKE);
        this.dottedStrokePaint.setStrokeWidth(strokeWidth);
        this.dottedStrokePaint.setPathEffect(new DashPathEffect(new float[] {20.0f, 7.5f}, 0.0f));

        this.alarms = Alarm.getAll(this.dbHelper);
        
        populate();
    }
    
    private Drawable dragDrawable = null;
    private boolean currentlyDraggingCircle = false;
    
    @Override
    public boolean onTouchEvent(MotionEvent event, MapView mapView)
    {
    	boolean handled = false;
    	AlarmItem focusItem = getFocus();
    	// Test for radius dragging before alarm dragging.  Otherwise the user can
    	// get stuck with a tiny circle.
    	if (focusItem != null
    		&& event.getAction() == MotionEvent.ACTION_DOWN
			&& distance(mapView, event, focusItem) < currCircleRadius + strokeWidth * 2
			&& distance(mapView, event, focusItem) > currCircleRadius - strokeWidth * 2)
    	{
    		currentlyDraggingCircle = true;
    		handled = true;
    	}
    	// Need to do actual drawable hit detection here, but hitTest doesn't
    	// work as I'd expect, so do a distance test.
    	else if (focusItem != null
    		&& event.getAction() == MotionEvent.ACTION_DOWN
			&& distance(mapView, event, focusItem) < 20)
    	{
  			this.dragDrawable = boundCenterBottom(this.arwi.getResources().getDrawable(R.drawable.pin_grey));
    	}
    	
    	if (event.getAction() == MotionEvent.ACTION_MOVE && this.dragDrawable != null)
        {
    		Rect bounds = this.dragDrawable.copyBounds();
            bounds.offsetTo(((int)event.getX()) - this.dragDrawable.getIntrinsicWidth() / 2, ((int)event.getY()) - this.dragDrawable.getIntrinsicHeight());
            // Leave room for a finger.
            bounds.offset(0, FINGER_Y_OFFSET);
            this.dragDrawable.setBounds(bounds);
            mapView.postInvalidate();
            handled = true;
        }
    	else if (event.getAction() == MotionEvent.ACTION_MOVE && currentlyDraggingCircle)
    	{
    		currCircleRadius = distance(mapView, event, focusItem);
    		handled = true;
    	}
        if (event.getAction() == MotionEvent.ACTION_UP)
        {
        	GeoPoint newGeoPoint = mapView.getProjection().fromPixels((int)event.getX(), (int)event.getY() + FINGER_Y_OFFSET);
            if (this.dragDrawable != null)
            {
            	moveItemTo(focusItem, newGeoPoint);
            	this.dragDrawable = null;
            	// Keep our item from losing focus.
            	handled = true;
            }
            if (this.currentlyDraggingCircle)
            {
            	GeoPoint curCenter = focusItem.getPoint();
            	//all the unused variables are just to inspect the results of various
            	//distance calcuations.  
            	float pixels = distance(mapView, event, focusItem);
            	float meters = distanceFromPoints(curCenter, newGeoPoint);
            	float meters2 = distanceFromPoints2(curCenter, newGeoPoint);
            	float meters3 = distanceFromPoints3(curCenter, newGeoPoint);
            	setItemRadius(focusItem, meters3);
            	//setItemRadius(focusItem, currCircleRadius);
            	this.currentlyDraggingCircle = false;
            	handled = true;
            }
        }
        return handled;
    }
    
    private float distance(MapView mapView, MotionEvent event, AlarmItem focusItem)
    {
    	return distance(event.getX(), event.getY(), mapView.getProjection().toPixels(focusItem.getPoint(), null));
	}

    private float distance(float x, float y, Point point)
    {
    	return (float)Math.sqrt(square(x - point.x) + square(y - point.y));
    }
    
    private float square(float x)
    {
    	return x * x;
    }
    
    private void moveItemTo(AlarmItem item, GeoPoint geoPoint)
    {
    	  
    	Alarm alarm = Alarm.findAlarm(dbHelper, item.id);
    	alarm.moveTo(dbHelper, geoPoint);
    	
        //Would probably be more efficient to do the inserts of name and point in one call but
    	//figured at some point we'd want to let people rename their alarms, so left them decoupled
    	final String name = getFormattedGeoCode(geoPoint); 
    	alarm.setName(dbHelper, name);
    	this.alarms = Alarm.getAll(dbHelper);
    	populate();
    	AlarmItem newItem = null;
    	for (int i = 0; i < size(); i++)
    	{
    		if (getItem(i).id == item.id)
    		{
    			Assert.assertNull(newItem);
    			newItem = getItem(i);
    		}
    	}
    	Assert.assertNotNull(newItem);
    	this.setFocus(newItem);
    }
    
    private void setItemRadius(AlarmItem item, float radius)
    {
    	Alarm alarm = Alarm.findAlarm(dbHelper, item.id);
    	alarm.setRadius(dbHelper, radius);
    	this.alarms = Alarm.getAll(dbHelper);
    	populate();
    	AlarmItem newItem = null;
    	for (int i = 0; i < size(); i++)
    	{
    		if (getItem(i).id == item.id)
    		{
    			Assert.assertNull(newItem);
    			newItem = getItem(i);
    		}
    	}
    	Assert.assertNotNull(newItem);
    	this.setFocus(newItem);
    }
    
	@Override
    protected AlarmItem createItem(int i)
    {
        return this.alarms[i].toOverlayItem();
    }

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow)
    {
        AlarmItem item = getFocus();
        if (item != null)
        {
            Projection projection = mapView.getProjection();
            GeoPoint geoPoint = item.getPoint();
            Point point = projection.toPixels(geoPoint, null);
            if (!currentlyDraggingCircle)
            {
            	//We are now attempting to store the meters, but this conversion back
            	//does not seem to work all that well.  Perhaps we're storing something weird
            	//but the calculation of meters from pixels on the screen is now using Google's
            	//own api to do the calculation.  But if you put in breakpoints and follow the
            	//values it appears that the pixel distance goes in, but the pixels that come out
            	//from this conversion are off.  The meters in and out are the same, so it's not
            	//a problem writing to the db.  Best guess right now is that this meters to 
            	//pixels function isn't all that great.  Need to poke at it more though.  Could
            	//just be that projections suck and there's no good way to do this, at least
            	//not just storing the radius.  We could consider storing the point of the radius
            	//that the user draws, and then just draw the radius next time based on that.
            	//Though I suppose we're still going to have to calculate the circle at some point.
            	//But we could just use the Location.distanceBetween call to detect.
            	float pixels = projection.metersToEquatorPixels(item.radiusMeters);
            	currCircleRadius = pixels;
            	
            	//Old way we just stored the pixels, not the meters
            	//currCircleRadius = item.radiusMeters;
            	drawCircle(canvas, this.fillPaint, point, currCircleRadius);
            	drawCircle(canvas, this.strokePaint, point, currCircleRadius);
            	drawCircle(canvas, this.strokePaint, point, miniCircleRadius);
            }
            else
            {
            	drawCircle(canvas, this.dottedStrokePaint, point, currCircleRadius);
            }
        }
        if (this.dragDrawable != null)
        {
            this.dragDrawable.draw(canvas);
        }
        
        super.draw(canvas, mapView, shadow);
    }

    private void drawCircle(Canvas canvas, Paint paint, Point center, float radius)
    {
        RectF circleRect = new RectF(center.x - radius, center.y - radius, center.x + radius, center.y + radius);
        canvas.drawOval(circleRect, paint);
    }
    
    @Override
    protected boolean onTap(int i)
    {
        AlarmItem item = getItem(i);
        setFocus(item);
        MapView mapView = (MapView)this.arwi.findViewById(R.id.mapview);
        mapView.getController().animateTo(item.getPoint());
        Toast.makeText(
                        this.arwi,
                        item.getSnippet(),
                        Toast.LENGTH_SHORT).show();
        return true;
    }
    
    @Override
    public int size()
    {
        return this.alarms.length;
    }

    public void deleteAll()
    {
        this.alarms = Alarm.deleteAll(this.dbHelper);
        super.setFocus(null);
        populate();
    }

    public void create(GeoPoint mapCenter)
    {
    	final String name = getFormattedGeoCode(mapCenter);        
        this.alarms = Alarm.create(this.dbHelper, name, mapCenter, Math.round(currCircleRadius));
        Toast.makeText(
                        this.arwi,
                        name,
                        Toast.LENGTH_SHORT).show();
        populate();
    }
    
    private String getFormattedGeoCode(GeoPoint location) 
    {
        Geocoder geocoder = new Geocoder(this.arwi);
        List<Address> addresses;
        try
        {
            addresses = geocoder.getFromLocation(location.getLatitudeE6() / 1000000.0, location.getLongitudeE6() / 1000000.0, 1);
        }
        catch (IOException e)
        {
            Log.e(Arwi.TAG, e.toString());
            addresses = Collections.emptyList();
        }
        final String name;
        if (addresses.size() == 0)
        {
            Log.e(Arwi.TAG, "No address could be found.");
            name = "Unknown location";
        }
        else
        {
            int size = addresses.get(0).getMaxAddressLineIndex() + 1;
            StringBuilder string = new StringBuilder();
            for (int i = 0; i < size; i++)
            {
                String addressLine = addresses.get(0).getAddressLine(i);
                if (string.length() != 0)
                {
                    string.append(", ");
                }
                string.append(addressLine);
            }
            name = string.toString();
        }
        return name;
    }
    
    /**
     * Calculates the distance in meters using the spherical law of cosines.  Port from
     * javascript at http://www.movable-type.co.uk/scripts/latlong.html  It is supposed to 
     * be approximate.  It appears to not work at all, must have translated something wrong
     * @param p1 first point
     * @param p2 second point
     * @return approximation of distance in meters
     */
    public float distanceFromPoints(GeoPoint p1, GeoPoint p2)
    {
    	//I think I need to divide these by 1000000, shoudl check
    	double lat1 = p1.getLatitudeE6() / 1000000.0;
    	double lat2 = p2.getLatitudeE6() / 1000000.0;
    	double lon1 = p1.getLongitudeE6() / 1000000.0;
    	double lon2 = p2.getLongitudeE6() / 1000000.0;
    	
    	double R = 6371; // km
    	double d = Math.acos(Math.sin(lat1)*Math.sin(lat2) + 
    	                  Math.cos(lat1)*Math.cos(lat2) *
    	                  Math.cos(lon2-lon1)) * R;
    	return new Float(d);
    }
    
    /**
     * Calculates the distance in meters using the Android Location distance between static 
     * static method.  
     * @param p1 first point
     * @param p2 second point
     * @return approximation of distance in meters
     */
    public float distanceFromPoints3(GeoPoint p1, GeoPoint p2)
    {
    	double lat1 = p1.getLatitudeE6() / 1000000.0;
    	double lat2 = p2.getLatitudeE6() / 1000000.0;
    	double lon1 = p1.getLongitudeE6() / 1000000.0;
    	double lon2 = p2.getLongitudeE6() / 1000000.0;
    	float[] results = new float[1];
    	Location.distanceBetween(lat1, lon1, lat2, lon2, results);
    	return results[0];
    }
    
    /**
     * Calculates the distance in meters using the Haversine formula.  Port from
     * javascript at http://www.movable-type.co.uk/scripts/latlong.html  It is supposed to 
     * be approximate, assuming the earth is a sphere, but should calculate quickly.
     * @param p1 first point
     * @param p2 second point
     * @return approximation of distance in meters
     */
    public float distanceFromPoints2(GeoPoint p1, GeoPoint p2)
    {
    	double lat1 = p1.getLatitudeE6() / 1000000.0;
    	double lat2 = p2.getLatitudeE6() / 1000000.0;
    	double lon1 = p1.getLongitudeE6() / 1000000.0;
    	double lon2 = p2.getLongitudeE6() / 1000000.0;
    	
    	double R = 6371; // km
    	double dLat = Math.toRadians((lat2 - lat1));
    	double dLon = Math.toRadians((lon2 - lon1));
    	double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
    	        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * 
    	        Math.sin(dLon/2) * Math.sin(dLon/2); 
    	double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a)); 
    	double d = R * c;
    	return new Float(d * 1000.0);
    }
}
