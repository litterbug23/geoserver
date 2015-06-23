/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.wms.worldwind;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;
import javax.media.jai.Interpolation;
import javax.media.jai.JAI;
import javax.media.jai.TiledImage;
import javax.media.jai.operator.FormatDescriptor;

import org.geoserver.catalog.MetadataMap;
import org.geoserver.data.util.CoverageUtils;
import org.geoserver.platform.ServiceException;
import org.geoserver.wms.GetMapRequest;
import org.geoserver.wms.MapLayerInfo;
import org.geoserver.wms.MapProducerCapabilities;
import org.geoserver.wms.WMS;
import org.geoserver.wms.WMSMapContent;
import org.geoserver.wms.map.RenderedImageMapResponse;
import org.geoserver.wms.worldwind.util.BilWCSUtils;
import org.geoserver.wms.worldwind.util.RecodeRaster;
import org.geotools.coverage.CoverageFactoryFinder;
import org.geotools.coverage.grid.GeneralGridEnvelope;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.geometry.DirectPosition2D;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.referencing.GeodeticCalculator;
import org.geotools.resources.coverage.CoverageUtilities;
import org.geotools.util.logging.Logging;
import org.opengis.geometry.coordinate.Position;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;
import org.vfny.geoserver.wcs.WcsException;

import com.sun.media.imageioimpl.plugins.raw.RawImageWriterSpi;
import com.vividsolutions.jts.geom.Envelope;


/**
 * Map producer for producing octet-stream out of an elevation model. Based on
 * the BilMapResponse
 * 
 * @author Juha Hyvärinen / Cyberlightning Ltd
 * @since 2.8.x
 * 
 */

public final class OctetStreamResponse extends RenderedImageMapResponse {

    /** A logger for this class. */
    private static final Logger LOGGER = Logging.getLogger(OctetStreamResponse.class);

    /** the only MIME type this map producer supports */
    static final String MIME_TYPE = "application/octet-stream";

    private static final String[] OUTPUT_FORMATS = { MIME_TYPE };

    /** GridCoverageFactory. - Where do we use this again ? */
    private final static GridCoverageFactory factory = CoverageFactoryFinder
            .getGridCoverageFactory(null);

    /** Raw Image Writer **/
    private final static ImageWriterSpi writerSPI = new RawImageWriterSpi();

    /**
     * Constructor for a {@link OctetStreamResponse}.
     *
     * @param wms
     *            that is asking us to encode the image.
     */
    public OctetStreamResponse(final WMS wms) {
        super(OUTPUT_FORMATS, wms);
    }

    @Override
    public void formatImageOutputStream(RenderedImage image, OutputStream outStream,
            WMSMapContent mapContent) throws ServiceException, IOException {

        // Get BIL layer configuration. This configuration is set by the server
        // administrator using the BIL layer config panel.
        final GetMapRequest request = mapContent.getRequest();
        List<MapLayerInfo> reqlayers = request.getLayers();
        MapLayerInfo mapLayerInfo = reqlayers.get(0);
        MetadataMap metadata = mapLayerInfo.getResource().getMetadata();

        String byteOrder = (String) metadata.get(BilConfig.BYTE_ORDER);

        final int height = request.getHeight();
        final int width = request.getWidth();

        // Render terrain from first layer
        if (reqlayers.size() > 1) {
            LOGGER.log(Level.WARNING,
                    "More than one layer in Octet-Stream request! Using only the first one!");
        }
        TiledImage terrain = renderTerrain(mapLayerInfo, metadata, mapContent, width, height);

        final ImageOutputStream imageOutStream = ImageIO.createImageOutputStream(outStream);

        // Set byte order out of output stream based on layer configuration.
        // Use big endian as default value
        if (ByteOrder.LITTLE_ENDIAN.toString().equals(byteOrder)) {
            imageOutStream.setByteOrder(ByteOrder.LITTLE_ENDIAN);
        } else {
            imageOutStream.setByteOrder(ByteOrder.BIG_ENDIAN);
        }

        imageOutStream.writeInt(width);
        imageOutStream.writeInt(height);

        CoordinateReferenceSystem crs = mapContent.getCoordinateReferenceSystem();

        Envelope env = request.getBbox();

        float distanceY = 0.0f;
        float distanceX = 0.0f;

        Position startPosition = new DirectPosition2D(crs, env.getMinX(), env.getMinY());
        Position stopXPosition = new DirectPosition2D(crs, env.getMaxX(), env.getMinY());
        Position stopYPosition = new DirectPosition2D(crs, env.getMinX(), env.getMaxY());

        GeodeticCalculator geodeticCalculator = new GeodeticCalculator(crs);
        try {
            geodeticCalculator.setStartingPosition(startPosition);
            geodeticCalculator.setDestinationPosition(stopYPosition);
            distanceY = (float) geodeticCalculator.getOrthodromicDistance() / height;

            geodeticCalculator.setDestinationPosition(stopXPosition);
            distanceX = (float) geodeticCalculator.getOrthodromicDistance() / width;
        } catch (IllegalStateException | TransformException e) {
            LOGGER.log(Level.SEVERE, "Error while calculating point distances", e.getStackTrace());
        }

        imageOutStream.writeFloat(distanceX);
        imageOutStream.writeFloat(distanceY);

        // Write terrain data
        final ImageWriter writer = writerSPI.createWriterInstance();
        writer.setOutput(imageOutStream);
        writer.write(terrain);

        imageOutStream.flush();
        imageOutStream.close();

    }

    private TiledImage renderTerrain(MapLayerInfo mapLayerInfo, MetadataMap metadata,
            WMSMapContent mapContent, int width, int height) {

        Double outNoData = null;
        Object noDataParam = metadata.get(BilConfig.NO_DATA_OUTPUT);
        if (noDataParam instanceof Number) {
            outNoData = ((Number) noDataParam).doubleValue();
        } else if (noDataParam instanceof String) {
            try {
                outNoData = Double.parseDouble((String) noDataParam);
            } catch (NumberFormatException e) {
                // TODO localize
                LOGGER.warning("Can't parse output no data attribute: " + e.getMessage());
            }
        }

        // wms.getCatalog().getCoverageByName(layerName).getGridCoverage(listener,
        // envelope, hints);
        GridCoverage2DReader coverageReader = null;
        try {
            coverageReader = (GridCoverage2DReader) mapLayerInfo.getCoverageReader();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        GeneralEnvelope destinationEnvelope = new GeneralEnvelope(mapContent.getRenderingArea());

        /*
         * Try to use a gridcoverage style render
         */
        GridCoverage2D subCov = null;
        try {
            subCov = getFinalCoverage(mapLayerInfo, mapContent, coverageReader, destinationEnvelope);
        } catch (Exception e) {
            LOGGER.severe("Could not get a subcoverage");
        }

        if (subCov == null) {
            LOGGER.fine("Creating coverage from a blank image");
            BufferedImage emptyImage = new BufferedImage(width, height,
                    BufferedImage.TYPE_USHORT_GRAY);
            DataBuffer data = emptyImage.getRaster().getDataBuffer();
            for (int i = 0; i < data.getSize(); ++i) {
                data.setElem(i, 32768); // 0x0080 in file (2^15)
            }
            subCov = factory.create("uselessname", emptyImage, destinationEnvelope);
        }

        if (subCov != null) {
            RenderedImage image = subCov.getRenderedImage();
            int dtype = image.getData().getDataBuffer().getDataType();

            RenderedImage transformedImage = image;

            // Perform NoData translation
            final double[] inNoDataValues = CoverageUtilities
                    .getBackgroundValues((GridCoverage2D) subCov);
            if (inNoDataValues != null && outNoData != null) {
                // TODO should support multiple no-data values
                final double inNoData = inNoDataValues[0];

                if (inNoData != outNoData) {
                    ParameterBlock param = new ParameterBlock().addSource(image);
                    param = param.add(inNoData);
                    param = param.add(outNoData);
                    transformedImage = JAI.create(RecodeRaster.OPERATION_NAME, param, null);
                }
            }

            // Perform format conversion. Client expects that data is float
            // values and therefore perform conversion if needed.
            if (dtype != DataBuffer.TYPE_FLOAT) {
                transformedImage = FormatDescriptor.create(transformedImage, DataBuffer.TYPE_FLOAT,
                        null);
            }

            return new TiledImage(transformedImage, width, height);
        } else {
            throw new ServiceException("Error occurred while getting subcoverage!");
        }

    }

    /**
     * getFinalCoverage - message the RenderedImage into Bil
     *
     * @param request
     *            CoverageRequest
     * @param meta
     *            CoverageInfo
     * @param mapContent
     *            Context for GetMap request.
     * @param coverageReader
     *            reader
     * 
     * @return GridCoverage2D
     * 
     * @throws Exception
     *             an error occurred
     */
    private static GridCoverage2D getFinalCoverage(MapLayerInfo meta, WMSMapContent mapContent,
            GridCoverage2DReader coverageReader, GeneralEnvelope destinationEnvelope)
            throws WcsException, IOException, IndexOutOfBoundsException, FactoryException,
            TransformException {

        final GetMapRequest request = mapContent.getRequest();

        // This is the final Response CRS
        final String responseCRS = request.getSRS();

        // - then create the Coordinate Reference System
        final CoordinateReferenceSystem targetCRS = CRS.decode(responseCRS);

        // This is the CRS of the requested Envelope
        final String requestCRS = request.getSRS();

        // - then create the Coordinate Reference System
        final CoordinateReferenceSystem sourceCRS = CRS.decode(requestCRS);

        // This is the CRS of the Coverage Envelope
        final CoordinateReferenceSystem cvCRS = ((GeneralEnvelope) coverageReader
                .getOriginalEnvelope()).getCoordinateReferenceSystem();

        // this is the destination envelope in the coverage crs
        final GeneralEnvelope destinationEnvelopeInSourceCRS = CRS.transform(destinationEnvelope,
                cvCRS);

        /**
         * Reading Coverage on Requested Envelope
         */
        Rectangle destinationSize = null;
        destinationSize = new Rectangle(0, 0, request.getHeight(), request.getWidth());

        /**
         * Checking for supported Interpolation Methods
         */
        Interpolation interpolation = Interpolation.getInstance(Interpolation.INTERP_BILINEAR);

        // /////////////////////////////////////////////////////////
        //
        // Reading the coverage
        //
        // /////////////////////////////////////////////////////////
        Map<Object, Object> parameters = new HashMap<Object, Object>();
        parameters.put(AbstractGridFormat.READ_GRIDGEOMETRY2D.getName().toString(),
                new GridGeometry2D(new GeneralGridEnvelope(destinationSize),
                        destinationEnvelopeInSourceCRS));

        final GridCoverage2D coverage = coverageReader.read(CoverageUtils.getParameters(
                coverageReader.getFormat().getReadParameters(), parameters, true));

        if (coverage == null) {
            LOGGER.log(Level.FINE, "Failed to read coverage - continuing");
            return null;
        }

        /**
         * Band Select
         */
        /*
         * Coverage bandSelectedCoverage = null;
         * 
         * bandSelectedCoverage = WCSUtils.bandSelect(request.getParameters(),
         * coverage);
         */
        /**
         * Crop
         */
        final GridCoverage2D croppedGridCoverage = BilWCSUtils.crop(coverage,
                (GeneralEnvelope) coverage.getEnvelope(), cvCRS, destinationEnvelopeInSourceCRS,
                Boolean.TRUE);

        /**
         * Scale/Resampling (if necessary)
         */
        // GridCoverage2D subCoverage = null;
        GridCoverage2D subCoverage = croppedGridCoverage;
        final GeneralGridEnvelope newGridrange = new GeneralGridEnvelope(destinationSize);

        subCoverage = BilWCSUtils.scale(croppedGridCoverage, newGridrange, croppedGridCoverage,
                cvCRS, destinationEnvelopeInSourceCRS);

        /**
         * Reproject
         */
        subCoverage = BilWCSUtils.reproject(subCoverage, sourceCRS, targetCRS, interpolation);

        return subCoverage;
    }

    /**
     * This is not really an image map
     */
    @Override
    public MapProducerCapabilities getCapabilities(String outputFormat) {
        // FIXME become more capable
        return new MapProducerCapabilities(false, false, false, false, null);
    }

    static {
        RecodeRaster.register(JAI.getDefaultInstance());
    }

}
