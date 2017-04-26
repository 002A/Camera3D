package camera3D.generators;

import java.io.File;
import java.util.ArrayList;

import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PImage;

/**
 * 
 * Monoscopic 360 Video Generator
 * 
 * @author James Schmitz
 * 
 */

public class Monoscopic360Generator extends Generator {

    private final static double PI = 3.14159265359;

    private int frameWidth;
    private int frameHeight;

    private PImage projectionFrame;
    private int projectionWidth; // width of composite frame in pixels
    private String saveLocation;
    private String panelExplainPlanLocation;
    private boolean displayCompositeFrame;

    private Vector cameraDirection;
    private Vector cameraUp;

    private ArrayList<Panel> panels;
    private int[] arrayIndex;
    private int[] pixelMapping;
    private int frameCount;

    public Monoscopic360Generator(int width, int height) {
        this.frameWidth = width;
        this.frameHeight = height;
        this.projectionWidth = 3 * width;
        this.saveLocation = null;
        this.panelExplainPlanLocation = null;
        this.frameCount = 0;
        this.displayCompositeFrame = true;

        initPanels();
    }

    public Monoscopic360Generator setOutputSizeAndLocation(int size,
            String saveLocation) {
        if (size / 2 != size / 2.0) {
            throw new RuntimeException("Size must be an even number");
        }
        this.projectionWidth = size;
        this.saveLocation = saveLocation;

        initPanels();

        return this;
    }

    public Monoscopic360Generator setPanelExplainPlanLocation(
            String panelExplainPlanLocation) {
        this.panelExplainPlanLocation = panelExplainPlanLocation;

        return this;
    }

    public Monoscopic360Generator skipDisplayingCompositeFrame() {
        this.displayCompositeFrame = false;

        return this;
    }

    public Monoscopic360Generator setThreadCount(int threadCount) {
        initExecutor(threadCount);

        return this;
    }

    private void initPanels() {
        // initialize the 6 panels with the correct settings
        panels = new ArrayList<Panel>();
        panels.add(new Panel(0, CameraOrientation.ABOVE, 0, 1, 0, 1));
        panels.add(new Panel(0, CameraOrientation.FRONT, 0, 1, 0, 1));
        panels.add(new Panel(0, CameraOrientation.REAR, 0, 1, 0, 1));
        panels.add(new Panel(0, CameraOrientation.LEFT, 0, 1, 0, 1));
        panels.add(new Panel(0, CameraOrientation.RIGHT, 0, 1, 0, 1));
        panels.add(new Panel(0, CameraOrientation.BELOW, 0, 1, 0, 1));

        // invalidate lookup tables so they are recalculated
        arrayIndex = null;
        pixelMapping = null;
    }

    private void calculatePixelMaps(PApplet parent) {
        arrayIndex = new int[projectionWidth * projectionWidth / 2];
        pixelMapping = new int[projectionWidth * projectionWidth / 2];

        // precompute the sin and cos LUTs
        double[] sinThetaLUT = new double[projectionWidth];
        double[] cosThetaLUT = new double[projectionWidth];
        for (int x = 0; x < projectionWidth; ++x) {
            double theta = -((2 * PI) * (x + 0.5) / projectionWidth - PI);
            sinThetaLUT[x] = Math.sin(theta);
            cosThetaLUT[x] = Math.cos(theta);
        }
        double[] sinPhiLUT = new double[projectionWidth / 2];
        double[] cosPhiLUT = new double[projectionWidth / 2];
        for (int y = 0; y < projectionWidth / 2; ++y) {
            double phi = (PI * (y + 0.5) / (projectionWidth / 2));
            sinPhiLUT[y] = Math.sin(phi);
            cosPhiLUT[y] = Math.cos(phi);
        }

        // for each pixel in the composite frame, reverse the equirectangular
        // projection to get the spherical coordinates. Then find which
        // panel to use and the best pixel.
        int prevPanel = 0;
        for (int x = 0; x < projectionWidth; ++x) {
            for (int y = 0; y < projectionWidth / 2; ++y) {
                // guess the correct panel is the same as the previous pixel
                Integer index = panels.get(prevPanel).locate(sinThetaLUT[x],
                        cosThetaLUT[x], sinPhiLUT[y], cosPhiLUT[y]);
                if (index != null) {
                    arrayIndex[y * projectionWidth + x] = prevPanel;
                    pixelMapping[y * projectionWidth + x] = index;
                    continue;
                }

                // guess was incorrect...try the others
                int p;
                for (p = 0; p < panels.size(); ++p) {
                    if (p == prevPanel) {
                        // already tried this one
                        continue;
                    }
                    index = panels.get(p).locate(sinThetaLUT[x],
                            cosThetaLUT[x], sinPhiLUT[y], cosPhiLUT[y]);
                    if (index != null) {
                        arrayIndex[y * projectionWidth + x] = p;
                        pixelMapping[y * projectionWidth + x] = index;
                        prevPanel = p;
                        break;
                    }
                }
                if (p == panels.size()) {
                    System.out.println(String.format("panel miss: (%d, %d)", x,
                            y));
                    arrayIndex[y * projectionWidth + x] = -1;
                    pixelMapping[y * projectionWidth + x] = 0;
                }
            }
        }

        panels.removeIf(Panel::unused);

        if (panelExplainPlanLocation != null) {
            PImage arrayIndexFrame = parent.createImage(projectionWidth,
                    projectionWidth / 2, PConstants.RGB);
            arrayIndexFrame.loadPixels();
            for (int i = 0; i < arrayIndex.length; ++i) {
                if (arrayIndex[i] < 0) {
                    arrayIndexFrame.pixels[i] = 0xFF000000;
                } else {
                    int gray = 255 * arrayIndex[i] / (panels.size() - 1);
                    arrayIndexFrame.pixels[i] = 0xFFFF0000 | (gray << 8) | gray;
                }
            }
            arrayIndexFrame.updatePixels();
            arrayIndexFrame.save(panelExplainPlanLocation);
        }
    }

    public int getComponentCount() {
        return panels.size();
    }

    public String getComponentFrameName(int frameNum) {
        if (0 <= frameNum && frameNum < panels.size()) {
            return panels.get(frameNum).getName();
        } else {
            return "";
        }
    }

    protected void recalculateCameraSettings() {
        cameraDirection = new Vector(
                (float) (config.cameraTargetX - config.cameraPositionX),
                (float) (config.cameraTargetY - config.cameraPositionY),
                (float) (config.cameraTargetZ - config.cameraPositionZ));
        cameraUp = new Vector((float) config.cameraUpX,
                (float) config.cameraUpY, (float) config.cameraUpZ);
    }

    public void prepareForDraw(int frameNum, PApplet parent) {
        frameCount = parent.frameCount;

        if (frameNum == 0) {
            projectionFrame = parent.createImage(projectionWidth,
                    projectionWidth / 2, PConstants.RGB);
        }

        panels.get(frameNum).setCamera(parent);

        if (arrayIndex == null || pixelMapping == null) {
            calculatePixelMaps(parent);
        }
    }

    public void generateCompositeFrame(int[] pixelDest, int[][] pixelStorage) {
        projectionFrame.loadPixels();
        executeTask(
                projectionFrame.pixels.length,
                (int start, int end) -> {
                    for (int ii = start; ii < end; ++ii) {
                        if (arrayIndex[ii] >= 0) {
                            projectionFrame.pixels[ii] = pixelStorage[arrayIndex[ii]][pixelMapping[ii]];
                        }
                    }
                });
        projectionFrame.updatePixels();

        // save compositeFrame to file
        if (saveLocation != null) {
            String filename = insertFrame(saveLocation, frameCount);
            projectionFrame.save(filename);

            if (frameCount == 1) {
                checkDiskSpace(filename);
            }
        }

        if (displayCompositeFrame) {
            projectionFrame.resize(frameWidth, 0); // slow - find faster way
            System.arraycopy(new int[pixelDest.length], 0, pixelDest, 0,
                    pixelDest.length);
            System.arraycopy(projectionFrame.pixels, 0, pixelDest, 0,
                    Math.min(projectionFrame.pixels.length, pixelDest.length));
        }
    }

    public void completedDraw(int frameNum, PApplet parent) {
        // do nothing
    }

    public void cleanup(PApplet parent) {
        parent.camera(config.cameraPositionX, config.cameraPositionY,
                config.cameraPositionZ, config.cameraTargetX,
                config.cameraTargetY, config.cameraTargetZ, config.cameraUpX,
                config.cameraUpY, config.cameraUpZ);
    }

    private enum CameraOrientation {
        FRONT, LEFT, REAR, RIGHT, ABOVE, BELOW
    }

    private void checkDiskSpace(String filename) {
        File file = new File(filename);
        File dir = file.getAbsoluteFile().getParentFile();

        long mb = (long) Math.pow(2, 20);
        double gb = Math.pow(2, 30);
        long filesize = file.length();
        long usablespace = dir.getUsableSpace();

        System.out.println("Saving frames to directory "
                + dir.getAbsolutePath());
        System.out.printf("There is %.2fGB available\n", usablespace / gb);
        System.out
                .printf("Saving each frame takes about %dMB\n", filesize / mb);

        if (config.frameLimit > 0) {
            long totalBytes = filesize * config.frameLimit;
            System.out.printf("Saving %d frames will take %.2fGB\n",
                    config.frameLimit, totalBytes / gb);
            if (totalBytes > usablespace) {
                throw new RuntimeException(
                        "Not enough disk space to save requested frames!");
            }
        } else {
            long totalFrames = usablespace / filesize;
            System.out.printf("Available space for about %d frames\n",
                    totalFrames);
        }
    }

    private class Panel {

        private int id;
        private CameraOrientation orientation;
        private boolean used;
        private double startPanelX;
        private double endPanelX;
        private double startPanelY;
        private double endPanelY;

        public Panel(int id, CameraOrientation orientation, double startPanelX,
                double endPanelX, double startPanelY, double endPanelY) {
            this.id = id;
            this.orientation = orientation;
            this.used = false;
            this.startPanelX = startPanelX;
            this.endPanelX = endPanelX;
            this.startPanelY = startPanelY;
            this.endPanelY = endPanelY;
        }

        public String getName() {
            return orientation + "-" + id;
        }

        public boolean unused() {
            return !used;
        }

        public Integer locate(double sinTheta, double cosTheta, double sinPhi,
                double cosPhi) {
            double polarX = -0.5 * sinPhi * sinTheta;
            double polarY = -0.5 * cosPhi;
            double polarZ = -0.5 * sinPhi * cosTheta;

            double panelX = -1;
            double panelY = -1;

            switch (orientation) {
            case FRONT:
                if (polarZ < 0) {
                    double scale = -0.5 / polarZ;
                    panelX = 0.5 + scale * polarX;
                    panelY = 0.5 + scale * polarY;
                }
                break;
            case REAR:
                if (polarZ > 0) {
                    double scale = 0.5 / polarZ;
                    panelX = 0.5 - scale * polarX;
                    panelY = 0.5 + scale * polarY;
                }
                break;
            case ABOVE:
                if (polarY < 0) {
                    double scale = -0.5 / polarY;
                    panelX = 0.5 + scale * polarX;
                    panelY = 0.5 - scale * polarZ;
                }
                break;
            case BELOW:
                if (polarY > 0) {
                    double scale = 0.5 / polarY;
                    panelX = 0.5 + scale * polarX;
                    panelY = 0.5 + scale * polarZ;
                }
                break;
            case LEFT:
                if (polarX < 0) {
                    double scale = -0.5 / polarX;
                    panelX = 0.5 - scale * polarZ;
                    panelY = 0.5 + scale * polarY;
                }
                break;
            case RIGHT:
                if (polarX > 0) {
                    double scale = 0.5 / polarX;
                    panelX = 0.5 + scale * polarZ;
                    panelY = 0.5 + scale * polarY;
                }
                break;
            default:
                break;
            }

            int frameX = (int) Math.floor(frameWidth * (panelX - startPanelX)
                    / (endPanelX - startPanelX));
            int frameY = (int) Math.floor(frameHeight * (panelY - startPanelY)
                    / (endPanelY - startPanelY));

            if (frameX < 0 || frameX >= frameWidth || frameY < 0
                    || frameY >= frameHeight) {
                return null;
            } else {
                used = true;
                return frameY * frameWidth + frameX;
            }
        }

        public void setCamera(PApplet parent) {
            Vector direction;
            Vector up;

            switch (orientation) {
            case ABOVE:
                direction = cameraUp.mult(cameraDirection.magnitude()).mult(-1);
                up = cameraDirection.normalized();
                break;
            case LEFT:
                direction = cameraUp.cross(cameraDirection);
                up = cameraUp;
                break;
            case RIGHT:
                direction = cameraDirection.cross(cameraUp);
                up = cameraUp;
                break;
            case REAR:
                direction = cameraDirection.mult(-1);
                up = cameraUp;
                break;
            case BELOW:
                direction = cameraUp.mult(cameraDirection.magnitude());
                up = cameraDirection.normalized().mult(-1);
                break;
            case FRONT:
                direction = cameraDirection;
                up = cameraUp;
                break;
            default:
                throw new RuntimeException("Unknown Orientation");
            }

            parent.camera(config.cameraPositionX, config.cameraPositionY,
                    config.cameraPositionZ, config.cameraPositionX
                            + direction.v1, config.cameraPositionY
                            + direction.v2, config.cameraPositionZ
                            + direction.v3, up.v1, up.v2, up.v3);

            float frustumLeft = (float) (config.frustumNear * (2 * startPanelX - 1));
            float frustumRight = (float) (config.frustumNear * (2 * endPanelX - 1));
            float frustumBottom = (float) (config.frustumNear * (1 - 2 * endPanelY));
            float frustumTop = (float) (config.frustumNear * (1 - 2 * startPanelY));

            parent.frustum(frustumLeft, frustumRight, frustumBottom,
                    frustumTop, config.frustumNear, config.frustumFar);
        }
    }

    private class Vector {

        public float v1;
        public float v2;
        public float v3;

        public Vector(float v1, float v2, float v3) {
            this.v1 = v1;
            this.v2 = v2;
            this.v3 = v3;
        }

        public Vector mult(float c) {
            return new Vector(c * v1, c * v2, c * v3);
        }

        public float magnitude() {
            return (float) Math.sqrt(v1 * v1 + v2 * v2 + v3 * v3);
        }

        public Vector normalized() {
            return mult(1 / magnitude());
        }

        public Vector cross(Vector other) {
            return new Vector(v2 * other.v3 - v3 * other.v2, v3 * other.v1 - v1
                    * other.v3, v1 * other.v2 - v2 * other.v1);
        }
    }
}