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
import org.geotools.geometry.DirectPosition2D;
//import org.geotools.geometry.Envelope2D;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.GeodeticCalculator;
import org.geotools.util.logging.Logging;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.geometry.coordinate.Position;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;

import com.vividsolutions.jts.geom.Geometry;


public class OctetStreamBuilder {

    private Long startTime;
    private final static Logger LOGGER = Logging.getLogger(OctetStreamBuilder.class);
    
    
    private BufferedWriter writer = null;
    private OutputStream outputStream = null;
    private ReferencedEnvelope boundingBox = null;
    private Terrain terrain = null;
    private Integer LOD = null;
    private Integer width = null;
    private Integer height = null;
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

    public void setResolution(int width, int height) {
        this.width = width;
        this.height = height;
    }
    
    private void addGeometry(Geometry geometry, String id) throws IOException {

        // Check if geometry is really visible
        // ScreenMap ...
        if (terrain == null) {
            terrain = new Terrain(boundingBox, request.getCrs());

            if (width != null && height != null) {
                terrain.setTargetResolution(width, height);
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

            } else if (width != null && height != null) {
                // If LOD is available but it's not requested, guess LOD level from point distance
                // and requested resolution

                double distanceX = 0.0;
                double distanceY = 0.0;

                // Calculate distances in meters
                CoordinateReferenceSystem crs = boundingBox.getCoordinateReferenceSystem();

                Position startPosition = new DirectPosition2D(crs, boundingBox.getMinX(), boundingBox.getMinY());
                Position stopXPosition = new DirectPosition2D(crs, boundingBox.getMaxX(), boundingBox.getMinY());
                Position stopYPosition = new DirectPosition2D(crs, boundingBox.getMinX(), boundingBox.getMaxY());

                GeodeticCalculator geodeticCalculator = new GeodeticCalculator(crs);
                try {
                    geodeticCalculator.setStartingPosition(startPosition);
                    geodeticCalculator.setDestinationPosition(stopYPosition);
                    distanceY = (float) geodeticCalculator.getOrthodromicDistance() / height;

                    geodeticCalculator.setDestinationPosition(stopXPosition);
                    distanceX = (float) geodeticCalculator.getOrthodromicDistance() / width;
                } catch (TransformException e) {
                    LOGGER.log(Level.SEVERE, "Error while calculating point distances", e.getStackTrace());
                }

                LOD = layer.getClosestLodByDistance(distanceX, distanceY);

                // LOGGER.log(Level.INFO,
                // "Request with resolution: "+width+" "+height+" Using LOD: "+LOD);

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
