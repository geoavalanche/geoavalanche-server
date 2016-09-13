/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.geoavalanche.wps.curvature;

import junit.framework.TestCase;
import com.vividsolutions.jts.geom.Geometry;
import org.geotools.coverage.grid.GridCoverage2D;

/**
 *
 * @author geobart
 */
public class CurvatureIT extends TestCase {
    
    public CurvatureIT(String testName) {
        super(testName);
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }
    
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test of Curvature method, of class Curvature.
     */
    public void testCurvature() throws Exception {
        System.out.println("Curvature");
        GridCoverage2D dem = null;
        Geometry geomShape = null;
        Curvature.Curvature(dem, geomShape);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }
    
}

