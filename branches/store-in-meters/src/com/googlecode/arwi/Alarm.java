/*
 * Copyright (C) 2008  Patrick F. Linehan
 * 
 * See the LICENSE file for details.
 */
package com.googlecode.arwi;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.google.android.maps.GeoPoint;

class Alarm
{
    private Long id;
    private final String name;
    private final int latitudeE6;
    private final int longitudeE6;
    private final float radiusMeters;

    Alarm(Long id, String name, int latitudeE6, int longitudeE6, float radiusMeters)
    {
    	this.id = id;
        this.name = name;
        this.latitudeE6 = latitudeE6;
        this.longitudeE6 = longitudeE6;
        this.radiusMeters = radiusMeters;
    }

    public static Alarm[] getAll(DbHelper dbHelper)
    {
        List<Alarm> alarms = new ArrayList<Alarm>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query("alarms", new String[] { "id", "name",
				"latitudeE6", "longitudeE6", "radiusMeters" }, null, null, null, null, null);
        for (int i = 0; i < cursor.getCount(); i++)
        {
            cursor.moveToPosition(i);
            alarms.add(new Alarm(
            				Long.valueOf(cursor.getLong(0)),
                            cursor.getString(1),
                            (int)cursor.getLong(2),
                            (int)cursor.getLong(3),
                            (float)cursor.getFloat(4)));
        }

        cursor.close();
        db.close();

        return alarms.toArray(new Alarm[0]);
    }

    public static Alarm[] deleteAll(DbHelper dbHelper)
    {
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        database.delete("alarms", null, null);
        database.close();
        Alarm[] alarms = getAll(dbHelper);
        if (alarms.length != 0)
        {
            throw new IllegalArgumentException();
        }
        return alarms;
    }

    public AlarmItem toOverlayItem()
    {
        return new AlarmItem(
        				this.id.longValue(),
                        new GeoPoint(this.latitudeE6, this.longitudeE6),
                        this.radiusMeters,
                        this.name,
                        this.name);
    }

    public static Alarm[] create(
    		DbHelper dbHelper,
    		String pName,
			GeoPoint center,
			float radiusMeters) {
		SQLiteDatabase database = dbHelper.getWritableDatabase();
		database
				.execSQL(
						"INSERT INTO alarms (name, latitudeE6, longitudeE6, radiusMeters) VALUES (?,?,?,?)",
						new Object[] { pName,
								Long.valueOf(center.getLatitudeE6()),
								Long.valueOf(center.getLongitudeE6()),
								Float.valueOf(radiusMeters)});
		database.close();
		return getAll(dbHelper);
	}
    
    public void moveTo(DbHelper dbHelper, GeoPoint geoPoint)
    {
		SQLiteDatabase database = dbHelper.getWritableDatabase();
		database
				.execSQL(
						"UPDATE alarms SET latitudeE6 = ?, longitudeE6 = ? where id = ?",
						new Object[] {
								Long.valueOf(geoPoint.getLatitudeE6()),
								Long.valueOf(geoPoint.getLongitudeE6()),
								Long.valueOf(id)});
		database.close();
    }
    
    public void setName(DbHelper dbHelper, String name)
    {
		SQLiteDatabase database = dbHelper.getWritableDatabase();
		database
				.execSQL(
						"UPDATE alarms SET name = ? where id = ?",
						new Object[] {
								name,
								Long.valueOf(id)});
		database.close();
    }
    
    public void setRadius(DbHelper dbHelper, float radius)
    {
		SQLiteDatabase database = dbHelper.getWritableDatabase();
		database
				.execSQL(
						"UPDATE alarms SET radiusMeters = ? where id = ?",
						new Object[] {
								Float.valueOf(radius),
								Long.valueOf(id)});
		database.close();
    }

	public static Alarm findAlarm(DbHelper dbHelper, long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query("alarms", new String[] { "id", "name",
				"latitudeE6", "longitudeE6", "radiusMeters" }, "id = ?", new String[] {String.valueOf(id)}, null, null, null);
        Assert.assertEquals(1, cursor.getCount());
        cursor.moveToPosition(0);
        Alarm alarm = new Alarm(
            				Long.valueOf(cursor.getLong(0)),
                            cursor.getString(1),
                            (int)cursor.getLong(2),
                            (int)cursor.getLong(3),
                            (int)cursor.getFloat(4));

        cursor.close();
        db.close();
        
        return alarm;
	}
}
