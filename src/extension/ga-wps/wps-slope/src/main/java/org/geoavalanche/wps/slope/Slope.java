package org.geoavalanche.wps.slope;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import es.unex.sextante.core.OutputFactory;
import es.unex.sextante.core.OutputObjectsSet;
import es.unex.sextante.core.ParametersSet;
import es.unex.sextante.core.Sextante;
import es.unex.sextante.dataObjects.IRasterLayer;
import es.unex.sextante.exceptions.GeoAlgorithmExecutionException;
import org.geoserver.wps.sextante.GTOutputFactory;
import org.geoserver.wps.sextante.GTRasterLayer;
import es.unex.sextante.morphometry.slope.SlopeAlgorithm;
import es.unex.sextante.outputs.Output;
import es.unex.sextante.outputs.OutputRasterLayer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.processing.CoverageProcessor;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.factory.StaticMethodsProcessFactory;
import org.geotools.text.Text;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 * Slope
 *
 */
public class Slope extends StaticMethodsProcessFactory<Slope> {
    
    private static final Logger LOG = Logger.getLogger(Slope.class.getName());
    private static final CoverageProcessor PROCESSOR = CoverageProcessor.getInstance();
    
    /*
     * The output factory to use when calling geoalgorithms
     * This tells the algorithm how to create new data objects (layers
     * and tables)
     * The GTOutputFactory creates objects based on geotools
     * data objects (DataStore and GridCoverage)
     */
    private static OutputFactory outputFactory = new GTOutputFactory();
    
    public Slope() {
        super(Text.text("GeoAvalanche Slope"), "geoavalanche", Slope.class);
    }
    
    @DescribeProcess(title = "Slope", description = "Calculate slopes in a shape")
    @DescribeResult(description = "Raster result with slopes")
    public static GridCoverage2D Slope(
            @DescribeParameter(name = "dem", description = "DEM coverage") GridCoverage2D dem,
            @DescribeParameter(name = "shape", description = "Shape") Geometry geomShape        
    ) throws Exception {
        
        // get the bounds
        CoordinateReferenceSystem crs;
        if (geomShape.getUserData() instanceof CoordinateReferenceSystem) {
            crs = (CoordinateReferenceSystem) geomShape.getUserData();
        } else {
            // assume the geometry is in the same crs
            crs = dem.getCoordinateReferenceSystem();
        }
        GeneralEnvelope bounds = new GeneralEnvelope(new ReferencedEnvelope(geomShape.getEnvelopeInternal(), crs));

        // force it to a collection if necessary
        GeometryCollection roi;
        if (!(geomShape instanceof GeometryCollection)) {
            roi = geomShape.getFactory().createGeometryCollection(new Geometry[] { geomShape });
        } else {
            roi = (GeometryCollection) geomShape;
        }
        
        // perform the crops
        final ParameterValueGroup param = PROCESSOR.getOperation("CoverageCrop").getParameters();
        param.parameter("Source").setValue(dem);
        param.parameter("Envelope").setValue(bounds);
        param.parameter("ROI").setValue(roi);
        
        GridCoverage2D cropCov = (GridCoverage2D) PROCESSOR.doOperation(param);
        
        /*
         * Initialize the library.
         * This will load all the algorithms and resource strings.
         * Since no language code is passed, default language(en)
         * will be used
         */
        Sextante.initialize();
        
        //Zevenberger & Thorne method
        int method = SlopeAlgorithm.METHOD_ZEVENBERGEN;
        
        //Resulting values in radians
        int unit = SlopeAlgorithm.UNITS_RADIANS;
        
        GridCoverage2D ret = null;
        
        try {
            /*
             * To use this data we need to wrap it with an object
             * that implements the IRasterLayer, so SEXTANTE algorithms
             * can access it.
             * Since it is a Geotools object, we will use the Geotools
             * wrapper class GTRasterLayer
             */
            GTRasterLayer raster = new GTRasterLayer();
            raster.create(cropCov);

            /*
             * Instantiate the SlopeAlgorithm class
             */
            SlopeAlgorithm alg = new SlopeAlgorithm();

            /*
             * The first thing we have to do is to set up the input parameters
             */
            ParametersSet params = alg.getParameters();
            params.getParameter(SlopeAlgorithm.DEM).setParameterValue(raster);
            params.getParameter(SlopeAlgorithm.METHOD).setParameterValue(method);
            params.getParameter(SlopeAlgorithm.UNITS).setParameterValue(unit);

            /*
             *  This algorithm will generate a new raster layer.
             * We can select "where" to put the result. To do this, we
             * retrieve the output container and set the output channel,
             * which contains information about the destiny of the resulting
             * data. The most common way of using this is setting
             * a FileOutputChannel, which contains the information needed to
             * put the output to a file (basically a filename).
             * If we omit this, a FileOutputChannel will be used,
             * using a temporary filename.
             */
            OutputObjectsSet outputs = alg.getOutputObjects();
            Output out = outputs.getOutput(SlopeAlgorithm.SLOPE);

            OutputRasterLayer resSlope = (OutputRasterLayer) out.getOutputObject();

            /*
             * Execute the algorithm. We use no task monitor,
             * so we will not be able to monitor the progress
             * of the execution. SEXTANTE also provides a DefaultTaskMonitor,
             * which shows a simple progress bar, or you could make your
             * own one, implementing the ITaskMonitor interface
             *
             * The execute method returns true if everything went OK, or false if it
             * was canceled. Since we are not giving the user the chance to
             * cancel it (there is no task monitor), we do not care about the
             * return value.
             *
             * If something goes wrong, it will throw an exception.
             */
            alg.execute(null, outputFactory);

            IRasterLayer slope = (IRasterLayer) resSlope;

            ret = new GridCoverage2D("slopes", (GridCoverage2D) slope);
            
        } catch (GeoAlgorithmExecutionException ex) {
            
            LOG.log(Level.SEVERE, "An exception in the GeoAlgorithm has been thrown {0}", ex.toString());
            
        } catch (Exception e) {
            
            LOG.log(Level.SEVERE, "A generic exception has been thrown {0}", e.toString());
            
        }
        return ret;
    }
    
}
