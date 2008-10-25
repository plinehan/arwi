package com.googlecode.arwi;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.OverlayItem;

public class AlarmItem extends OverlayItem {
	final long id;
	final float radiusMeters;
	
	public AlarmItem(long id, GeoPoint point, float radiusMeters, String title, String snippet) {
		super(point, title, snippet);
		this.id = id;
		this.radiusMeters = radiusMeters;
	}
}
