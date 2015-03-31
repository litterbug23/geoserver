/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2012 - 2013 OpenPlans
 * 
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 *
 * @author Jorge Gustavo Rocha / Universidade do Minho
 * @author Nuno Carvalho Oliveira / Universidade do Minho
 * @author Juha Hyvärinen / Cyberlightning Ltd
 */

package org.geoserver.w3ds.types;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.w3ds.styles.Styles3D;
import org.geoserver.w3ds.utilities.X3DInfoExtract;
import org.geotools.feature.FeatureCollection;
import org.opengis.filter.Filter;
import org.geotools.styling.Style;
import org.opengis.feature.Feature;
import org.opengis.feature.type.FeatureType;


public class W3DSLayer {

	private W3DSLayerInfo layerInfo;
	private FeatureCollection<? extends FeatureType, ? extends Feature> features;
	private List<String> objectClass;
	private String objectID;
	private Boolean hasObjectID;
	private Boolean hasObjectClass;
	private Boolean haveLODs = false;
	private List<Style> styles;
	private Map<Integer, Double> lodDistances = null;

	public W3DSLayer(W3DSLayerInfo layerInfo,
			FeatureCollection<? extends FeatureType, ? extends Feature> features, Catalog catalog) throws IOException {
		this.layerInfo = layerInfo;
		this.features = features;
		X3DInfoExtract x3dInfoExtract = new X3DInfoExtract();
		x3dInfoExtract.setLayerInfo(layerInfo.getLayerInfo());

		if (x3dInfoExtract.haveLODS()) {
    		this.haveLODs = true;
    		this.lodDistances = x3dInfoExtract.getLodDistances();
		}

		if (x3dInfoExtract.haveObjectID()) {
			this.hasObjectID = true;
			this.objectID = x3dInfoExtract.getObjectID();
		} else {
			this.hasObjectID = false;
			this.objectID = "";
		}

		if (x3dInfoExtract.haveObjectClass()) {
			this.hasObjectClass = true;
			this.objectClass = x3dInfoExtract.getObjectClass();
		} else {
			this.hasObjectClass = false;
			this.objectClass = new ArrayList<String>();
		}

		this.styles = new ArrayList<Style>();
		StyleInfo styleInfo = layerInfo.getRequestStyle();
		if (styleInfo != null) {
			Styles3D styles3D = new Styles3D(catalog);
			this.styles.add(styles3D.getStyle(styleInfo));
			//this.styles.add(styleInfo.getStyle());
		}
		// Maybe some day ...
		/*for(StyleInfo si : layerInfo.getLayerInfo().getStyles()) {
			this.styles.add(si.getStyle());
		}*/
	}

	public W3DSLayerInfo getLayerInfo() {
		return layerInfo;
	}

	public void setLayerInfo(W3DSLayerInfo layerInfo) {
		this.layerInfo = layerInfo;
	}

	public boolean haveLODs() {
	    return haveLODs;
	}

	/**
	 * 
	 * @param lod
	 * @return Closest LOD to requested which is found from dataset.
	 */
	public int getClosestLod(int lod) {
	    if (lodDistances.containsKey(lod)) {
	        return lod;
	    } else {
	        // Find closest match
	        // Loop down from requested to 1
	        boolean found = false;
	        int i = lod;
	        while (i > 0) {
	            if (lodDistances.containsKey(i)) {
	                lod = i;
	                found = true;
	                break;
	            }
	            i--;
	        }

	        // If not found try to loop up from requested to 10
	        if (!found) {
	            while (i < 11) {
	                if (lodDistances.containsKey(i)) {
	                    lod = i;
	                    break;
	                }
	                i++;
	            }
	        }

	        return lod;
	    }
	}

	/**
	 * 
	 * @param distanceX point distance (meters) within x axis
	 * @param distanceY point distance (meters) within y axis
	 * @return LOD value with closest match  
	 */
	public int getClosestLodByDistance(double distanceX, double distanceY) {
	    double distance;
	    if (distanceX < distanceY) {
	        distance = distanceX;
	    } else if (distanceX > distanceY) {
	        distance = distanceY;
	    } else {
	        distance = distanceX;
	    }

	    int lod = 1;
	    int previous = 1;
	    for (int i = 10; i > 0; i--) {
	        if (lodDistances.containsKey(i)) {
	            if (lodDistances.get(i) > distance) {
	                if (previous < i) {
	                    lod = i;
	                } else {
	                    lod = previous;
	                }
    	            break;
	            }
	            previous = i;
	        }
	    }

	    return lod;
	}

	public FeatureCollection<? extends FeatureType, ? extends Feature> getFeatures() {
		return features;
	}

	public FeatureCollection<? extends FeatureType, ? extends Feature> getFeatures(Filter filter) {
            return features.subCollection(filter);
    }

	public void setFeatures(
			FeatureCollection<? extends FeatureType, ? extends Feature> features) {
		this.features = features;
	}

	public List<String> getObjectClass() {
		return objectClass;
	}

	public void setObjectClass(List<String> objectClass) {
		this.objectClass = objectClass;
	}

	public String getObjectID() {
		return objectID;
	}

	public void setObjectID(String objectID) {
		this.objectID = objectID;
	}

	public Boolean getHasObjectID() {
		return hasObjectID;
	}

	public void setHasObjectID(Boolean hasObjectID) {
		this.hasObjectID = hasObjectID;
	}

	public Boolean getHasObjectClass() {
		return hasObjectClass;
	}

	public void setHasObjectClass(Boolean hasObjectClass) {
		this.hasObjectClass = hasObjectClass;
	}
	
	public List<Style> getStyles() {
		return this.styles;
	}
	
	public void addStyle(Style style) {
		this.styles.add(style);
	}

}
