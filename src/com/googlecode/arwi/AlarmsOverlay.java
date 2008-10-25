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
            if (this.dragDrawable != null)
            {
            	GeoPoint newGeoPoint = mapView.getProjection().fromPixels((int)event.getX(), (int)event.getY() + FINGER_Y_OFFSET);
            	moveItemTo(focusItem, newGeoPoint);
            	this.dragDrawable = null;
            	// Keep our item from losing focus.
            	handled = true;
            }
            if (this.currentlyDraggingCircle)
            {
    
            	setItemRadius(focusItem, currCircleRadius);
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
    	alarm.setRadius(dbHelper, Math.round(radius));
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
            	//Uncommenting the following two lines and commenting the last will make it so 
            	//the size of the circle stays fixed relative the earth when zooming in and out.
            	//The only problem is that it gets recorded in screen pixels right now, so ends up as not
            	//many meters.  Need to calculate how many meters a radius is in meters and store that, and
            	//the projection classes offer no easy conversion back.
            	//float pixels = projection.metersToEquatorPixels(item.radiusMeters);
            	//currCircleRadius = pixels;
            	currCircleRadius = item.radiusMeters;
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
        Geocoder geocoder = new Geocoder(this.arwi);
        List<Address> addresses;
        try
        {
            addresses = geocoder.getFromLocation(mapCenter.getLatitudeE6() / 1000000.0, mapCenter.getLongitudeE6() / 1000000.0, 1);
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
        
        this.alarms = Alarm.create(this.dbHelper, name, mapCenter, Math.round(currCircleRadius));
        Toast.makeText(
                        this.arwi,
                        name,
                        Toast.LENGTH_SHORT).show();
        populate();
    }
}
