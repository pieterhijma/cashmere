/* $Id: BodyCanvas.java 3581 2006-03-20 16:03:59Z ceriel $ */

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;

class BodyCanvas extends Canvas {

    // Using an image to draw the bodies does not flicker.
    private static final boolean USE_IMAGE = true;

    // Use continous scaling to keep all particales in the image.
    private static final boolean USE_SCALING = false;

    private static final int BORDER = 5;

    private int width, height;

    private float[] positions;
    private float[] velocities;

    private double maxx, maxy, minx, miny;

    private BufferedImage img;

    static BodyCanvas visualize(float[] positions, float[] velocities) {
        JFrame.setDefaultLookAndFeelDecorated(true);

        //Create and set up the window.
        JFrame frame = new JFrame("Bodies");
        //frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        BodyCanvas bc = new BodyCanvas(500, 500, positions, velocities);
        frame.getContentPane().add(bc);

        //Display the window.
        frame.pack();
        frame.setVisible(true);

        return bc;
    }

    private BodyCanvas(int w, int h, float[] pos, float[] vel) {
        width = w;
        height = h;
        positions = pos;
        velocities = vel;

        //make it a little bigger so everything should fit
        setSize(w + BORDER * 2, h + BORDER * 2);

        if (USE_IMAGE) {
            img = new BufferedImage(w + BORDER * 2, h + BORDER * 2,
                    BufferedImage.TYPE_INT_RGB);
        }

        setBackground(Color.BLACK);

        //find the maximum and minimum values of x and y
        computeBoundaries();
    }

    //find the maximum and minimum values of x and y
    private void computeBoundaries() {
        for (int i = 0; i < positions.length/3; i++) {
            maxx = Math.max(positions[3*i], maxx);
            maxy = Math.max(positions[3*i+1], maxy);
            minx = Math.min(positions[3*i], minx);
            miny = Math.min(positions[3*i+1], miny);
        }

        //        System.err.println("min x" + min.x + " min y " + min.y + "max x " +
        // max.x + " max y " +max.y);

        if (!USE_SCALING) {
            minx *= 20;
            miny *= 20;
            maxx = -minx;
            maxy = -miny;
        }
    }

    public void update(Graphics g) {
        paint(g);
    }

    public synchronized void paint(Graphics g) {
        int i, x, y;

        if (USE_SCALING) {
            computeBoundaries();
        }

        if (USE_IMAGE) {
            // clear image
            for (int a = 0; a < height; a++) {
                for (int b = 0; b < width; b++) {
                    img.setRGB(b, a, 0);
                }
            }

            for (i = 0; i < positions.length/3; i++) {
                x = (int) ((positions[3*i] - minx) / (maxx - minx) * (double) width)
                    + BORDER;
                y = (int) ((positions[3*i+1] - miny) / (maxy - miny) * (double) height)
                    + BORDER;

                double col = velocities[3*i+2];
                col *= 255;

                int color = 0;
                if(col < 0) { // moving away: red
                    col = Math.abs(col);
                    col = Math.min(255, col);
                    col = Math.max(80, col);
                    color = ((int) col) << 16;
                } else { // moving to the viewer: blue
                    col = Math.min(255, col);
                    col = Math.max(80, col);
                    color = ((int) col);
                }

                if (x > 0 && x < width && y > 0 && y < height) {
                    img.setRGB(x, y, color);
                }
            }

            g.drawImage(img, 0, 0, null);
        } else {
            g.clearRect(0, 0, width, height);

            g.setColor(Color.WHITE);

            for (i = 0; i < positions.length/3; i++) {
                x = (int) ((positions[3*i] - minx) / (maxx - minx) * (double) width)
                    + BORDER;
                y = (int) ((positions[3*i+1] - miny) / (maxy - miny) * (double) height)
                    + BORDER;
                if (x > 0 && x < width && y > 0 && y < height) {
                    g.drawLine(x, y, x, y);
                    //            g.fillOval(x, y, 1, 1);
                }
            }
        }
    }
}
