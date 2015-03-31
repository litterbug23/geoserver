/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 * 
 * @author Juha Hyvärinen / Cyberlightning Ltd
 */

package org.geoserver.w3ds.responses;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.geoserver.ows.Response;
import org.geoserver.platform.Operation;
import org.geoserver.platform.ServiceException;
import org.geoserver.w3ds.octetstream.OctetStreamBuilder;
import org.geoserver.w3ds.types.GetSceneRequest;
import org.geoserver.w3ds.types.GetTileRequest;
import org.geoserver.w3ds.types.Scene;
import org.geoserver.w3ds.types.W3DSLayer;
import org.geoserver.w3ds.types.W3DSLayerInfo;
import org.geoserver.w3ds.utilities.Format;
import org.geotools.util.logging.Logging;

public class OctetStreamResponse extends Response {

    private final static Logger LOGGER = Logging.getLogger(OctetStreamResponse.class);

    public OctetStreamResponse() {
        super(Scene.class);
    }

    @Override
    public boolean canHandle(Operation operation) {
        Object requestObject = operation.getParameters()[0];

        // Check if request format is supported by this implementation
        if (requestObject instanceof GetTileRequest) {
            return false;
        } else if (requestObject instanceof GetSceneRequest) {
            GetSceneRequest getSceneRequest = (GetSceneRequest) requestObject;
            if (getSceneRequest.getFormat() == Format.OCTET_STREAM) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    @Override
    public String getMimeType(Object value, Operation operation) {
        Object op = operation.getParameters()[0];

        if (op instanceof GetSceneRequest) {
            return ((GetSceneRequest) op).getFormat().getMimeType();
        } else if (op instanceof GetTileRequest) {
            return ((GetTileRequest) op).getFormat().getMimeType();
        } else {
            return Format.XML3D.getMimeType();
        }
    }

    @Override
    public String getAttachmentFileName(Object value, Operation operation) {
        StringBuilder fileName = new StringBuilder();
        Object requestObject = operation.getParameters()[0];
        if (requestObject instanceof GetSceneRequest) {
            GetSceneRequest getSceneRequest = (GetSceneRequest) requestObject;
            for (W3DSLayerInfo w3dsLayerInfo : getSceneRequest.getLayers()) {
                fileName.append(w3dsLayerInfo.getLayerInfo().getName());
            }
        }
        // if (requestObject instanceof GetTileRequest) {
        // GetTileRequest getTileRequest = (GetTileRequest) requestObject;
        // fileName.append(getTileRequest.getLayer().getLayerInfo().getName());
        // }
        fileName.append(".bin");
        return fileName.toString();
    }

    @Override
    public void write(Object content, OutputStream outputStream, Operation operation)
            throws IOException {
        Object requestObject = operation.getParameters()[0];

        if (requestObject instanceof GetSceneRequest) {
            GetSceneRequest getSceneRequest = (GetSceneRequest) requestObject;
            writeGetScene((Scene) content, outputStream, getSceneRequest);
        } else {
            throw new ServiceException("The request is not recognized!");
        }
    }

    private void writeGetScene(Scene scene, OutputStream outputStream,
            GetSceneRequest getSceneRequest) throws IOException {
        OctetStreamBuilder octetStreamBuilder = new OctetStreamBuilder(getSceneRequest,
                outputStream);

        // Set LOD if it is requested
        if (getSceneRequest.getKpvPrs().containsKey("LOD")) {
            int LOD = Integer.parseInt(getSceneRequest.getKpvPrs().get("LOD"));
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("LOD Request with LOD value: " + LOD);
            }

            octetStreamBuilder.setLOD(LOD);
        }

        if (getSceneRequest.getKpvPrs().containsKey("resX")) {
            if (getSceneRequest.getKpvPrs().containsKey("resY")) {
                int resX = Integer.parseInt(getSceneRequest.getKpvPrs().get("resX"));
                int resY = Integer.parseInt(getSceneRequest.getKpvPrs().get("resY"));

                octetStreamBuilder.setResolution(resX, resY);
                LOGGER.log(Level.FINE, "Request with resolution parameters: resX = " + resX
                        + " resY = " + resY);
            } else {
                LOGGER.log(Level.SEVERE, "Request with resolution was missing another parameter! "
                        + "(miss: resY)");
            }
        } else if (getSceneRequest.getKpvPrs().containsKey("resY")) {
            LOGGER.log(Level.SEVERE, "Request with resolution was missing another parameter! "
                    + "(miss: resX)");
        }

        // Add layers
        for (W3DSLayer layer : scene.getLayers()) {
            octetStreamBuilder.addW3DSLayer(layer);
        }

        octetStreamBuilder.writeOutput();
        octetStreamBuilder.close();
    }

    // private void writeGetTile(Scene scene, OutputStream outputStream, GetTileRequest
    // getTileRequest)
    // throws IOException {
    // // TODO
    // }
}
