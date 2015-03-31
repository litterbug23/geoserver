/* (c) 2015 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.w3ds.octetstream;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.util.logging.Logging;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;

import org.geoserver.w3ds.types.Vector3;
import org.geoserver.w3ds.octetstream.TerrainReSampler;
import org.geoserver.w3ds.octetstream.TerrainReSampler.Algorithm;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 * 
 * @author Juha Hyvärinen / Cyberlightning Ltd
 *
 */

public class Terrain {

    private final static Logger LOGGER = Logging.getLogger(Terrain.class);

    private Double bbox[] = null;

    private List<Vector3> globalVertices = null;

    private CoordinateReferenceSystem crs;

    private Integer destResolutionX = null;

    private Integer destResolutionY = null;

    public Terrain(ReferencedEnvelope boundingBox, CoordinateReferenceSystem coordinateReferenceSystem) {
        this.crs = coordinateReferenceSystem;

        bbox = new Double[4];
        bbox[0] = boundingBox.getMinX(); // Min X
        bbox[1] = boundingBox.getMinY(); // Min Z

        bbox[2] = boundingBox.getMaxX(); // Max X
        bbox[3] = boundingBox.getMaxY(); // Max Z
    }

    public void setTargetResolution(int resX, int resY) {
        destResolutionX = resX;
        destResolutionY = resY;
    }

    public void addGeometry(Geometry geometry) {
        if (globalVertices == null) {
            globalVertices = new ArrayList<Vector3>();
        }
        Coordinate[] coordinates = geometry.getCoordinates();
        for (int i = 0; i < coordinates.length; i++) {
            Vector3 vertex = new Vector3(coordinates[i].x, coordinates[i].z, coordinates[i].y);
            if (!globalVertices.contains(vertex)) {
                globalVertices.add(vertex);
            }
        }
    }

    private List<List<Vector3>> createGrid(List<Vector3> vertices) {
        List<List<Vector3>> vertexGrid = new ArrayList<List<Vector3>>();
        float THRESHOLD = 0.00002f;

        for (int i = 0; i < vertices.size(); i++) {
            Vector3 vertex = vertices.get(i);

            // Find correct row for vertex and add it there.
            boolean found = false;
            for (int j = 0; j < vertexGrid.size(); j++) {
                // Add first vertex to empty row
                if (vertexGrid.get(j).size() == 0) {
                    vertexGrid.get(j).add(vertex);
                    found = true;
                    break;
                }
                Vector3 temp = vertexGrid.get(j).get(0);
                if (temp.z - THRESHOLD < vertex.z && temp.z + THRESHOLD > vertex.z) {
                    vertexGrid.get(j).add(vertex);
                    found = true;
                    break;
                }
            }

            // Row was not yet created
            if (!found) {
                vertexGrid.add(new ArrayList<Vector3>());
                vertexGrid.get(vertexGrid.size() - 1).add(vertex);
            }
        }

        return vertexGrid;
    }

    private List<List<Vector3>> sortGrid(List<List<Vector3>> grid) {
        // Sort columns with insertion sort
        int size = grid.size();
        for (int i = 0; i < size; i++) {
            // Get row for sorting
            List<Vector3> row = grid.get(i);

            // Sort vertices in that row
            for (int j = 0; j < row.size(); j++) {
                Vector3 vertex = row.get(j);
                int k = j - 1;
                while (k >= 0 && row.get(k).x < vertex.x) {
                    row.set(k + 1, row.get(k));
                    k = k - 1;
                }
                row.set(k + 1, vertex);
            }
        }

        // Sort rows with insertion sort
        for (int i = 0; i < size; i++) {
            List<Vector3> row = grid.get(i);
            int j = i - 1;
            while (j >= 0 && grid.get(j).get(0).z < row.get(0).z) {
                grid.set(j + 1, grid.get(j));
                j = j - 1;
            }
            grid.set(j + 1, row);
        }
        return grid;
    }

    private List<List<Vector3>> smoothGrid(List<List<Vector3>> grid, boolean right, boolean left,
            boolean upper, boolean lower) {

        int sizeX = grid.size() - 1;
        int sizeY = grid.get(0).size() - 1;

        if (!left) {
            // Up left corner
            if (!upper) {
                sizeY = grid.get(0).size() - 1;
                grid.get(0).get(sizeY).y = (grid.get(0).get(sizeY).y + grid.get(0).get(sizeY - 1).y
                        + grid.get(1).get(sizeY).y + grid.get(1).get(sizeY - 1).y) / 4;
            }

            // Low left corner
            if (!lower) {
                sizeY = grid.get(sizeX).size() - 1;
                grid.get(sizeX).get(sizeY).y = (grid.get(sizeX).get(sizeY).y
                        + grid.get(sizeX).get(sizeY - 1).y + grid.get(sizeX - 1).get(sizeY).y + grid
                        .get(sizeX - 1).get(sizeY - 1).y) / 4;
            }
            // Left side
            for (int i = 1; i < sizeX; i++) {
                sizeY = grid.get(i).size() - 1;

                grid.get(i).get(sizeY).y = (grid.get(i).get(sizeY).y + grid.get(i).get(sizeY - 1).y) / 2;
            }
        }

        if (!right) {
            // Up right corner
            if (!upper) {
                grid.get(0).get(0).y = (grid.get(0).get(0).y + grid.get(0).get(1).y
                        + grid.get(1).get(0).y + grid.get(1).get(1).y) / 4;
            }

            // Low right corner
            if (!lower) {
                grid.get(sizeX).get(0).y = (grid.get(sizeX).get(0).y + grid.get(sizeX).get(1).y
                        + grid.get(sizeX - 1).get(0).y + grid.get(sizeX - 1).get(1).y) / 4;
            }

            // Right side
            for (int i = 1; i < sizeX; i++) {
                grid.get(i).get(0).y = (grid.get(i).get(0).y + grid.get(i).get(1).y) / 2;
            }
        }

        // First row
        if (!upper) {
            sizeY = grid.get(0).size() - 1;
            for (int i = 1; i < sizeY; i++) {
                grid.get(0).get(i).y = (grid.get(0).get(i).y + grid.get(1).get(i).y) / 2;
            }
        }

        // Last row ( grid.get(sizeX).size()-2 == sizeY -1 )
        if (!lower) {
            sizeY = grid.get(sizeX).size() - 1;
            for (int i = 1; i < sizeY; i++) {
                grid.get(sizeX).get(i).y = (grid.get(sizeX).get(i).y + grid.get(sizeX - 1).get(i).y) / 2;
            }
        }

        return grid;
    }

    private List<List<Vector3>> checkBboxBorders(List<List<Vector3>> grid) {
        // This can potentially break terrain if source data is in some planar projection.
        // For example in tm35.
        boolean left = false;
        boolean right = false;
        boolean upper = false;
        boolean lower = false;

        for (int i = 0; i < globalVertices.size(); i++) {
            double threshold = 0.00001; // 0,000041667
            Vector3 vertex = globalVertices.get(i);
            if (vertex.x >= bbox[0] - threshold && vertex.x <= bbox[0] + threshold) {
                // point is within offset from left border
                left = true;
            } else if (vertex.x >= bbox[2] - threshold && vertex.x <= bbox[2] + threshold) {
                // point is within offset from right border
                right = true;
            } else if (vertex.z >= bbox[1] - threshold && vertex.z <= bbox[1] + threshold) {
                // point is within offset from lower border
                lower = true;
            } else if (vertex.z >= bbox[3] - threshold && vertex.z <= bbox[3] + threshold) {
                // point is within offset from upper border
                upper = true;
            }
        }

        if (left) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("point is within offset from left border\n");
            }
            for (int i = 0; i < grid.size(); i++) {
                grid.get(i).remove(grid.get(i).size() - 1);
            }
        }
        if (right) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("point is within offset from right border\n");
            }
            for (int i = 0; i < grid.size(); i++) {
                grid.get(i).remove(0);
            }
        }
        if (upper) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("point is within offset from upper border\n");
            }
            grid.remove(0);
        }
        if (lower) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("point is within offset from lower border\n");
            }
            grid.remove(grid.size() - 1);
        }

        grid = smoothGrid(grid, right, left, upper, lower);

        return grid;
    }

    public byte[] toByteArray() {
        List<List<Vector3>> grid = null;

        grid = createGrid(globalVertices);
        grid = sortGrid(grid);

        // LOGGER.log(Level.INFO, "Original grid: " + grid.size() + " " + grid.get(0).size());

        if (destResolutionX != null && destResolutionY != null) {
            TerrainReSampler resampler = new TerrainReSampler(Algorithm.BiCubic);
            grid = resampler.resample(grid, destResolutionX, destResolutionY);
        } else {
            // System.out.print(crs.getName().toString());
            if (crs.getName().toString().equalsIgnoreCase("EPSG:WGS 84")) {
                grid = checkBboxBorders(grid);
            } else {
                grid = smoothGrid(grid, false, false, false, false);
            }
        }

        int sizeX = grid.get(0).size();
        int sizeZ = grid.size();

        // Compute array resolution
        float distanceX = (float) (bbox[2] - bbox[0]) / sizeX;
        float distanceZ = (float) (bbox[3] - bbox[1]) / sizeZ;

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = null;
        byte outputByteArray[] = null;
        try {
            out = new DataOutputStream(bos);

            out.writeInt(sizeX);
            out.writeInt(sizeZ);

            out.writeFloat(distanceX);
            out.writeFloat(distanceZ);

            for (int i = 0; i < sizeZ; i++) {
                int len = grid.get(i).size() - 1;
                for (int j = len; j >= 0; j--) {
                    out.writeFloat((float) grid.get(i).get(j).y);
                }
            }
            out.flush();

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Points in first row: " + sizeX + " Rows: " + sizeZ);
                LOGGER.fine("ByteArray point distance in x: " + distanceZ + " in y: " + distanceZ
                        + "\n");
            }

            outputByteArray = bos.toByteArray();

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e.toString());
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
                bos.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, e.toString());
            }
        }

        return outputByteArray;
    }
}
