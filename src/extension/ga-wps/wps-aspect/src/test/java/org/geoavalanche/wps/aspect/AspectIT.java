/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.geoavalanche.wps.aspect;

import junit.framework.TestCase;
import com.vividsolutions.jts.geom.Geometry;
import org.geotools.coverage.grid.GridCoverage2D;

/**
 *
 * @author geobart
 */
public class AspectIT extends TestCase {
    
    public AspectIT(String testName) {
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
     * Test of Slope method, of class Aspect.
     */
    public void testSlope() throws Exception {
        System.out.println("Slope");
        GridCoverage2D dem = null;
        Geometry geomShape = null;
        Aspect.Slope(dem, geomShape);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }
    
}
