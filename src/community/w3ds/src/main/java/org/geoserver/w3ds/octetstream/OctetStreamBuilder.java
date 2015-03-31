/* (c) 2015 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 * 
 * @author Juha Hyvärinen / Cyberlightning Ltd
 */

package org.geoserver.w3ds.octetstream;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geoserver.platform.ServiceException;
import org.geoserver.w3ds.types.GetSceneRequest;
import org.geoserver.w3ds.types.W3DSLayer;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
//import org.geotools.geometry.Envelope2D;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.util.logging.Logging;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;

import com.vividsolutions.jts.geom.Geometry;


public class OctetStreamBuilder {

    private Long startTime;
    private final static Logger LOGGER = Logging.getLogger(OctetStreamBuilder.class);
    
    
    private BufferedWriter writer = null;
    private OutputStream outputStream = null;
    private ReferencedEnvelope boundingBox = null;
    private Terrain terrain = null;
    private Integer LOD = null;
    private Integer resX = null;
    private Integer resY = null;
    private GetSceneRequest request = null;
    

    public OctetStreamBuilder(GetSceneRequest SceneRequest, OutputStream output) {
        this.request = SceneRequest;
        boundingBox = request.getBbox();
        outputStream = output;

        writer = new BufferedWriter(new OutputStreamWriter(outputStream));
    }

    public void addW3DSLayer(W3DSLayer layer) {
        startTime = System.currentTimeMillis();

        FeatureCollection<?, ?> collection = getFeatureCollection(layer);
        FeatureIterator<?> iterator = collection.features();

        try {
            SimpleFeature feature = null;
            SimpleFeatureType fType = null;
            List<AttributeDescriptor> types = null;

            while (iterator.hasNext()) {
                feature = (SimpleFeature) iterator.next();
                fType = feature.getFeatureType();
                types = fType.getAttributeDescriptors();

                Geometry geometry = null;
                String id = null;

                for (int j = 0; j < types.size(); j++) {
                    Object value = feature.getAttribute(j);

                    if (value != null) {
                        if (value instanceof Geometry) {
                            id = layer.getLayerInfo().getRequestName() + ":"
                                    + getObjectID(layer, feature);
                            // classname = getObjectClass(layer, feature);
                            geometry = (Geometry) value;
                        }
                    }
                }
                addGeometry(geometry, id);
            }
            iterator.close();
            iterator = null;

        } catch (Exception exception) {
            ServiceException serviceException = new ServiceException("Error: "
                    + exception.getMessage());
            serviceException.initCause(exception);
            throw serviceException;
        } finally {
            if (iterator != null) {
                iterator.close();
                iterator = null;
            }
        }

    }
    
    public void setLOD(int lod) {
        LOD = lod;
    }

    public void setResolution(int resX, int resY) {
        this.resX = resX;
        this.resY = resY;
    }
    
    private void addGeometry(Geometry geometry, String id) throws IOException {

        // Check if geometry is really visible
        // ScreenMap ...
        if (terrain == null) {
            terrain = new Terrain(boundingBox, request.getCrs());

            if (resX != null && resY != null) {
                terrain.setTargetResolution(resX, resY);
            }
        }
        terrain.addGeometry(geometry);
    }

    private FeatureCollection<?, ?> getFeatureCollection(W3DSLayer layer) {
        FeatureCollection<?, ?> collection = null;

        if (layer.haveLODs()) {
            if (LOD != null) {
                LOD = layer.getClosestLod(LOD);
                FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
                Filter filter = ff.equals(ff.property("lod"), ff.literal(LOD));

                collection = layer.getFeatures(filter);

            } else if (resX != null && resY != null) {
                // If LOD is available but it's not requested, guess LOD level from point distance
                // and requested resolution

                double lowerCorner[] = { boundingBox.getMinX(), boundingBox.getMinY() };
                double upperCorner[] = { boundingBox.getMaxX(), boundingBox.getMaxY() };
                double distanceX = 0.0;
                double distanceY = 0.0;

                // Check bounding box CRS and compute distances in meters
                String crs = boundingBox.getCoordinateReferenceSystem().getName().toString();

                if (crs.equalsIgnoreCase("EPSG:WGS 84")) {
                    double[] point1 = { lowerCorner[0], lowerCorner[1] };
                    double[] point2 = { lowerCorner[0], upperCorner[1] };

                    distanceY = getDistanceBetweenPoints(point1, point2) / resY;

                    point1[0] = lowerCorner[0];
                    point1[1] = lowerCorner[1];

                    point2[0] = upperCorner[0];
                    point2[1] = lowerCorner[1];

                    distanceX = getDistanceBetweenPoints(point1, point2) / resX;

                } else {
                    // This works only with metric coordinate systems
                    distanceX = (upperCorner[0] - lowerCorner[0]) / resX;
                    distanceY = (upperCorner[1] - lowerCorner[1]) / resY;
                }

                LOD = layer.getClosestLodByDistance(distanceX, distanceY);

                // LOGGER.log(Level.INFO,
                // "Request with resolution: "+resX+" "+resY+" Using LOD: "+LOD);

                FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
                Filter filter = ff.equals(ff.property("lod"), ff.literal(LOD));

                collection = layer.getFeatures(filter);
            } else {
                // By default use lowest LOD available
                LOD = layer.getClosestLod(1);

                FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
                Filter filter = ff.equals(ff.property("lod"), ff.literal(LOD));

                collection = layer.getFeatures(filter);
            }
        } else {
            collection = layer.getFeatures();
        }

        return collection;
    }

    /**
     * Haversine formula: a = sin²(Δφ/2) + cos φ1 ⋅ cos φ2 ⋅ sin²(Δλ/2) c = 2 ⋅ atan2( √a, √(1−a) )
     * d = R ⋅ c where φ is latitude, λ is longitude, R is earth’s radius (mean radius = 6371km);
     *
     * @param point1
     *            [lon, lat]
     * @param point2
     *            [lon, lat]
     * @return distance (meters)
     */
    private double getDistanceBetweenPoints(double[] point1, double[] point2) {

        int R = 6378137; // earth's mean radius in m

        // Coordinates are in lon - lat order
        double lat1 = Math.toRadians(point1[1]);
        double lat2 = Math.toRadians(point2[1]);

        double lon1 = Math.toRadians(point1[0]);
        double lon2 = Math.toRadians(point2[0]);

        double Δφ = lat2 - lat1;
        double Δλ = lon2 - lon1;

        double a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(Δλ / 2) * Math.sin(Δλ / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    

    private static String getObjectID(W3DSLayer layer, Feature feature) {
        if (!layer.getHasObjectID()) {
            return "unknown";
        }
        String id = layer.getObjectID();
        Object o = feature.getProperty(id).getValue();
        if (o != null) {
            return feature.getProperty(id).getValue().toString();
        }
        return "unknown";
    }

    // private static String getObjectClass(W3DSLayer layer, Feature feature) {
    // if (!layer.getHasObjectClass()) {
    // return "";
    // }
    // StringBuilder strb = new StringBuilder();
    // for (String cn : layer.getObjectClass()) {
    // Object o = feature.getProperty(cn).getValue();
    // if (o != null) {
    // strb.append(feature.getProperty(cn).getValue().toString() + " ");
    // }
    // }
    // return strb.toString();
    // }


    /*
     * Write byte array with size information of planar component, resolution and elevation data for
     * each point in that planar.
     */
    public void writeOutput() {
        try {
            if (terrain != null) {
                outputStream.write(terrain.toByteArray());
            }
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (LOGGER.isLoggable(Level.INFO))
            LOGGER.info("Octet-Stream request handling took: "
                    + (System.currentTimeMillis() - startTime) + "ms");
    }

    public void close() throws IOException {
        writer.flush();
        writer.close();
    }

}
