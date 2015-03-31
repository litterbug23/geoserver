/* (c) 2015 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 * 
 * @author Juha Hyvärinen / Cyberlightning Ltd
 */

package org.geoserver.w3ds.octetstream;

import java.util.ArrayList;
import java.util.List;

import org.geoserver.w3ds.types.Vector3;

public class TerrainReSampler {
    private SubSampledData horizontallySubsampledData;
    private SubSampledData verticallySubsampledData;
    private Algorithm algorithm;

    public enum Algorithm {
        BiCubic;
    }

    interface InterpolationAlgorithm {
        public float apply(float value);

        public float getSamplingRadius();

        public String getName();
    }

    class BiCubicInterpolation implements InterpolationAlgorithm {

        final protected float a;

        public BiCubicInterpolation() {
            a = -0.5f;
        }

        protected BiCubicInterpolation(float a) {
            this.a = a;
        }

        public final float apply(float value) {
            if (value == 0)
                return 1.0f;
            if (value < 0.0f)
                value = -value;
            float vv = value * value;
            if (value < 1.0f) {
                return (a + 2f) * vv * value - (a + 3f) * vv + 1f;
            }
            if (value < 2.0f) {
                return a * vv * value - 5 * a * vv + 8 * a * value - 4 * a;
            }
            return 0.0f;
        }

        public float getSamplingRadius() {
            return 2.0f;
        }

        public String getName() {
            return "BiCubic";
        }
    }

    static class SubSampledData {
        private final int[] arrN; // individual - per row or per column - nr of contributions
        private final int[] valueArray; // 2Dim: [wid or hei][contrib]
        private final float[] weightArray; // 2Dim: [wid or hei][contrib]
        private final int contributors; // the primary index length for the 2Dim arrays :
                                           // arrPixel and arrWeight

        private SubSampledData(int[] arrN, int[] arrPixel, float[] arrWeight, int contributors) {
            this.arrN = arrN;
            this.valueArray = arrPixel;
            this.weightArray = arrWeight;
            this.contributors = contributors;
        }

        public int getNumContributors() {
            return contributors;
        }

        public int[] getArrN() {
            return arrN;
        }

        public int[] getValueArray() {
            return valueArray;
        }

        public float[] getWeightArray() {
            return weightArray;
        }
    }

    public TerrainReSampler(Algorithm a) {
        this.algorithm = a;
    }

    public List<List<Vector3>> resample(List<List<Vector3>> grid, int destWidth, int destHeight) {
        if (destWidth < 3 || destHeight < 3) {
            throw new RuntimeException("Error doing rescale. Target size was " + destWidth + "x"
                    + destHeight + " but must be at least 3x3.");
        }

        int srcWidth = grid.get(0).size();
        int srcHeight = grid.size();

        InterpolationAlgorithm interpolation = null;
        if (algorithm == Algorithm.BiCubic) {
            interpolation = new BiCubicInterpolation();
        }

        double[][] workGrid = new double[srcHeight][destWidth];

        // Pre-calculate sub-sampling
        horizontallySubsampledData = createSubSampling(interpolation, srcWidth, destWidth);
        verticallySubsampledData = createSubSampling(interpolation, srcHeight, destHeight);

        final List<List<Vector3>> srcGridCopy = grid;
        final double[][] workGridCopy = workGrid;

        horizontallyFromSrcToWork(srcGridCopy, workGridCopy, destWidth);

        double[] outPixels = new double[destWidth * destHeight];
        final double[] outCopy = outPixels;

        verticallyFromWorkToDst(workGridCopy, outCopy, destWidth, destHeight);

        ArrayList<List<Vector3>> tmp = new ArrayList<List<Vector3>>();
        for (int i = 0; i < destHeight; i++) {
            tmp.add(new ArrayList<Vector3>());
            for (int j = 0; j < destWidth; j++) {
                tmp.get(i).add(new Vector3(0, outCopy[i * destWidth + j], 0));
            }
        }

        return tmp;

    }

    private SubSampledData createSubSampling(InterpolationAlgorithm interpolation, int srcSize,
            int destSize) {
        float scale = (float) destSize / (float) srcSize;
        int[] arrN = new int[destSize];
        int contributors;
        float[] weightArray;
        int[] valueArray;

        final float fwidth = interpolation.getSamplingRadius();

        float centerOffset = 0.5f / scale;

        if (scale < 1.0f) {
            final float width = fwidth / scale;
            contributors = (int) (width * 2.0f + 2);
            weightArray = new float[destSize * contributors];
            valueArray = new int[destSize * contributors];

            final float fNormFac = (float) (1f / (Math.ceil(width) / fwidth));

            for (int i = 0; i < destSize; i++) {
                final int subindex = i * contributors;
                float center = i / scale + centerOffset;
                int left = (int) Math.floor(center - width);
                int right = (int) Math.ceil(center + width);
                for (int j = left; j <= right; j++) {
                    float weight;
                    weight = interpolation.apply((center - j) * fNormFac);

                    if (weight == 0.0f) {
                        continue;
                    }
                    int n;
                    if (j < 0) {
                        n = -j;
                    } else if (j >= srcSize) {
                        n = srcSize - j + srcSize - 1;
                    } else {
                        n = j;
                    }
                    int k = arrN[i];

                    arrN[i]++;
                    if (n < 0 || n >= srcSize) {
                        weight = 0.0f;// Flag that cell should not be used
                    }
                    valueArray[subindex + k] = n;
                    weightArray[subindex + k] = weight;
                }
                // normalize the filter's weight's so the sum equals to 1.0, very important for
                // avoiding box type of artifacts
                final int max = arrN[i];
                float tot = 0;
                for (int k = 0; k < max; k++)
                    tot += weightArray[subindex + k];
                if (tot != 0f) { // 0 should never happen except bug in filter
                    for (int k = 0; k < max; k++)
                        weightArray[subindex + k] /= tot;
                }
            }
        } else
        // super-sampling
        // Scales from smaller to bigger height
        {
            contributors = (int) (fwidth * 2.0f + 1);
            weightArray = new float[destSize * contributors];
            valueArray = new int[destSize * contributors];
            //
            for (int i = 0; i < destSize; i++) {
                final int subindex = i * contributors;
                float center = i / scale + centerOffset;
                int left = (int) Math.floor(center - fwidth);
                int right = (int) Math.ceil(center + fwidth);
                for (int j = left; j <= right; j++) {
                    float weight = interpolation.apply(center - j);
                    if (weight == 0.0f) {
                        continue;
                    }
                    int n;
                    if (j < 0) {
                        n = -j;
                    } else if (j >= srcSize) {
                        n = srcSize - j + srcSize - 1;
                    } else {
                        n = j;
                    }
                    int k = arrN[i];
                    arrN[i]++;
                    if (n < 0 || n >= srcSize) {
                        weight = 0.0f;// Flag that cell should not be used
                    }
                    valueArray[subindex + k] = n;
                    weightArray[subindex + k] = weight;
                }
                // normalize the filter's weight's so the sum equals to 1.0, very important for
                // avoiding box type of artifacts
                final int max = arrN[i];
                float tot = 0;
                for (int k = 0; k < max; k++)
                    tot += weightArray[subindex + k];
                assert tot != 0 : "should never happen except bug in filter";
                if (tot != 0f) {
                    for (int k = 0; k < max; k++)
                        weightArray[subindex + k] /= tot;
                }
            }
        }
        return new SubSampledData(arrN, valueArray, weightArray, contributors);
    }

    private void verticallyFromWorkToDst(double[][] workGrid, double[] outValues, int destWidth,
            int destHeight) {

        for (int x = 0; x < destWidth; x++) {
            final int xLocation = x;
            for (int y = destHeight - 1; y >= 0; y--) {
                final int yTimesContributors = y * verticallySubsampledData.contributors;
                final int max = verticallySubsampledData.arrN[y];
                final int sampleLocation = (y * destWidth + x);

                double sample = 0.0f;
                int index = yTimesContributors;
                for (int j = max - 1; j >= 0; j--) {
                    int valueLocation = verticallySubsampledData.valueArray[index];
                    float arrWeight = verticallySubsampledData.weightArray[index];
                    sample += workGrid[valueLocation][xLocation] * arrWeight;

                    index++;
                }

                outValues[sampleLocation] = sample;
            }

        }
    }

    private void horizontallyFromSrcToWork(List<List<Vector3>> srcGrid, double[][] workGrid,
            int destWidth) {

        int srcHeight = srcGrid.size();

        // create reusable row to minimize memory overhead
        List<Vector3> srcValues = new ArrayList<Vector3>();

        for (int k = 0; k < srcHeight; k++) {
            srcValues = srcGrid.get(k);

            for (int i = destWidth - 1; i >= 0; i--) {
                int sampleLocation = i;
                final int max = horizontallySubsampledData.arrN[i];

                double sample0 = 0.0f;
                int index = i * horizontallySubsampledData.contributors;
                for (int j = max - 1; j >= 0; j--) {
                    float weight = horizontallySubsampledData.weightArray[index];
                    int valueIndex = horizontallySubsampledData.valueArray[index];

                    sample0 += srcValues.get(valueIndex).y * weight;
                    index++;
                }

                workGrid[k][sampleLocation] = sample0;
            }
        }
    }
}
