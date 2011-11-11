/*
 * ScatterPlot.java
 *
 * Copyright (C) 2002-2009 Alexei Drummond and Andrew Rambaut
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * BEAST is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package dr.gui.chart;

import dr.stats.Variate;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Vector;
import java.util.Set;

/**
 * Description:	A scatter plot.
 *
 * @author Andrew Rambaut
 * @author Alexei Drummond
 * @version $Id: ScatterPlot.java,v 1.10 2005/05/24 20:25:59 rambaut Exp $
 */

public class ScatterPlot extends Plot.AbstractPlot {

    private Vector<Rectangle2D> markBounds = null;

    protected Stroke hilightedMarkStroke = new BasicStroke(0.5f);
    protected Paint hilightedMarkPaint = Color.black;
    protected Paint hilightedMarkFillPaint = Color.blue;

    /**
     * Constructor
     */
    public ScatterPlot(Variate xData, Variate yData) {
        super(xData, yData);
        setMarkStyle(CIRCLE_MARK, 5, new BasicStroke(1),
                Color.black, Color.yellow);
        setHilightedMarkStyle(new BasicStroke(1),
                Color.black, Color.blue);
    }

    /**
     * Constructor
     */
    public ScatterPlot(double[] xData, double[] yData) {
        super(xData, yData);
        setMarkStyle(CIRCLE_MARK, 5, new BasicStroke(1),
                Color.black, Color.yellow);
        setHilightedMarkStyle(new BasicStroke(1),
                Color.black, Color.blue);
    }

    /**
     * Set mark style
     */
    public void setHilightedMarkStyle(Stroke hilightedMarkStroke,
                               Paint hilightedMarkPaint,
                               Paint hilightedMarkFillPaint) {

        this.hilightedMarkStroke = hilightedMarkStroke;
        this.hilightedMarkPaint = hilightedMarkPaint;
        this.hilightedMarkFillPaint = hilightedMarkFillPaint;
    }

    /**
     * Draw a mark transforming co-ordinates to each axis
     */
    protected void drawMark(Graphics2D g2, float x, float y) {

        Rectangle2D bounds = mark.getBounds2D();
        float w = (float) bounds.getWidth();
        float h = (float) bounds.getHeight();
        x = x - (w / 2);
        y = y - (h / 2);

        g2.translate(x, y);

        if (markFillPaint != null) {
            g2.setPaint(markFillPaint);
            g2.fill(mark);
        }

        g2.setPaint(markPaint);
        g2.setStroke(markStroke);
        g2.draw(mark);

        g2.translate(-x, -y);

        Rectangle2D rect = new Rectangle2D.Float(x, y, w, h);
        markBounds.add(rect);
    }

    /**
     * Draw a mark transforming co-ordinates to each axis
     */
    protected void drawMarkHilighted(Graphics2D g2, float x, float y) {

        Rectangle2D bounds = mark.getBounds2D();
        float w = (float) bounds.getWidth();
        float h = (float) bounds.getHeight();
        x = x - (w / 2);
        y = y - (h / 2);

        g2.translate(x, y);

        if (hilightedMarkFillPaint != null) {
            g2.setPaint(hilightedMarkFillPaint);
            g2.fill(mark);
        }

        g2.setPaint(hilightedMarkPaint);
        g2.setStroke(hilightedMarkStroke);
        g2.draw(mark);

        g2.translate(-x, -y);

        Rectangle2D rect = new Rectangle2D.Float(x, y, w, h);
        markBounds.add(rect);
    }

    /**
     * Paint data series
     */
    protected void paintData(Graphics2D g2, Variate xData, Variate yData) {

        float x, y;

        markBounds = new java.util.Vector<Rectangle2D>();

        Set<Integer> selectedPoints = getSelectedPoints();

        int n = xData.getCount();
        for (int i = 0; i < n; i++) {
            x = (float) transformX(xData.get(i));
            y = (float) transformY(yData.get(i));

            if (selectedPoints.contains(i)) {
                drawMarkHilighted(g2, x, y);
            } else {
                drawMark(g2, x, y);
            }
        }

    }

    /**
     * A point on the plot has been clicked
     */
    public void pointClicked(Point2D point) {

        double x = untransformX(point.getX());
        double y = untransformY(point.getY());

        if (markBounds != null) {
            int mark = -1;

            for (int i = 0; i < markBounds.size(); i++) {
                if ((markBounds.get(i)).contains(point)) {
                    mark = i;
                    break;
                }
            }
            fireMarkClickedEvent(mark, x, y);
		}

		firePointClickedEvent(x, y);
	}

}
