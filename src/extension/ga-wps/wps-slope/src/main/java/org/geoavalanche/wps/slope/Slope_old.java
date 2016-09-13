package org.geoavalanche.wps.slope;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import es.unex.sextante.core.AnalysisExtent;
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
import es.unex.sextante.outputs.FileOutputChannel;
import es.unex.sextante.outputs.OutputRasterLayer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.measure.unit.Unit;
import org.geotools.coverage.GridSampleDimension;
import org.geotools.coverage.grid.GridCoordinates2D;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.processing.CoverageProcessor;
import org.geotools.coverage.processing.Operations;
import org.geotools.geometry.DirectPosition2D;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.measure.Units;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.factory.StaticMethodsProcessFactory;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultDerivedCRS;
import org.geotools.referencing.crs.DefaultEngineeringCRS;
import org.geotools.referencing.crs.DefaultImageCRS;
import org.geotools.referencing.crs.DefaultProjectedCRS;
import org.geotools.text.Text;
import org.opengis.coverage.Coverage;
import org.opengis.coverage.SampleDimension;
import org.opengis.coverage.grid.GridGeometry;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.ProjectedCRS;

/**
 * Slope_old
 *
 */
public class Slope_old extends StaticMethodsProcessFactory<Slope_old> {
    
    private static final Logger LOG = Logger.getLogger(Slope_old.class.getName());
    private static final CoverageProcessor PROCESSOR = CoverageProcessor.getInstance();
    
    /*
     * The output factory to use when calling geoalgorithms
     * This tells the algorithm how to create new data objects (layers
     * and tables)
     * The GTOutputFactory creates objects based on geotools
     * data objects (DataStore and GridCoverage)
     */
    private static OutputFactory outputFactory = new GTOutputFactory();
    private static final double METRES_PER_DEGREE = 111320;
    
    public Slope_old() {
        super(Text.text("GeoAvalanche"), "geoavalanche", Slope_old.class);
    }
    
    @DescribeProcess(title = "Slope", description = "Calculate slopes in a shape")
    @DescribeResult(description = "Raster result with slopes")
    public static GridCoverage2D Slope (
            @DescribeParameter(name = "dem", description = "DEM coverage") GridCoverage2D dem,
            @DescribeParameter(name = "shape", description = "Shape") Geometry geomShape        
    ) throws Exception {
        
        // initialize the return
        GridCoverage2D ret = null;

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

        //return (GridCoverage2D) PROCESSOR.doOperation(param);
        
        GridCoverage2D cropCov = (GridCoverage2D) PROCESSOR.doOperation(param);
        LOG.log(Level.INFO, "cropCov dimensions are {0}", cropCov.getSampleDimensions());
        
//        LOG.log(Level.INFO, "Dimensions are {0}", cropCov.getSampleDimensions());
//        
        GridSampleDimension[] sdArray = cropCov.getSampleDimensions();
//        
//        GridCoverage2D realElev = null;
//        
        for (int i = 0; i < sdArray.length; i++) {
            
            LOG.log(Level.INFO, "sample dimensions are {0}", sdArray[i].getCategoryNames().toString());
//          
            LOG.log(Level.INFO, "Min value is {0}", sdArray[i].getMinimumValue());
            LOG.log(Level.INFO, "Max value is {0}", sdArray[i].getMaximumValue());
//            Unit u = sdArray[i].getUnits();
//            LOG.log(Level.INFO, "Unit is {0}", u.toString());
//            
//            if (!cropCov.getCoordinateReferenceSystem().getCoordinateSystem().getAxis(0).getUnit().equals(u)) {
//            
//                // perform the multiplyConst
//                final ParameterValueGroup par = PROCESSOR.getOperation("MultiplyConst").getParameters();
//                par.parameter("Source").setValue(cropCov);
//                //Double[] mxd = null;
//                //mxd[1] = (Double) METRES_PER_DEGREE;
//                double[] mxd = new double[] { METRES_PER_DEGREE };
//                LOG.log(Level.INFO, "mxd is {0}", Arrays.toString(mxd));
//                par.parameter("constants").setValue(mxd);
//                realElev = (GridCoverage2D) PROCESSOR.doOperation(par);
//            }
//            
        }
        
        
        /*
         * Initialize the library.
         * This will load all the algorithms and resource strings.
         * Since no language code is passed, default language(en)
         * will be used
         */
        Sextante.initialize();
        
        //Zevenberger & Thorne method
        int method = SlopeAlgorithm.METHOD_ZEVENBERGEN;
        
        //Resulting values in degree
        int unit = SlopeAlgorithm.UNITS_DEGREES;
        
        
        try {

            LOG.log(Level.INFO, "Just a point to check if Sextante algorithms are {0}", Sextante.getAlgorithms().get("SEXTANTE").toString());            

            /*
             * To use this data we need to wrap it with an object
             * that implements the IRasterLayer, so SEXTANTE algorithms
             * can access it.
             * Since it is a Geotools object, we will use the Geotools
             * wrapper class GTRasterLayer
             */
            GTRasterLayer raster = new GTRasterLayer();
            raster.create(cropCov);
            LOG.log(Level.INFO, "CRS for cropped raster is {0}",raster.getCRS().toString());
            
            LOG.log(Level.INFO, "raster basedata is {0}",raster.getBaseDataObject().toString());
            
            
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

            //Setting z-factor
            //take the grid extent of a dem
            AnalysisExtent ge = raster.getLayerGridExtent();
            ////change it cellsize. This will automatically recalculate number of
            ////rows and cols
            ge.setCellSize(111320.);
            //
            ////Set that grid extent as the one to use to produce new raster layers
            ////from the algorithm
            alg.setAnalysisExtent(ge);
            
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
            //out.setOutputChannel(new FileOutputChannel("/tmp/slope.tif"));

            alg.execute(null, outputFactory);

            LOG.log(Level.INFO, "outputObject of out is {0}", out.getOutputObject().toString());

            IRasterLayer resSlope = (IRasterLayer) out.getOutputObject();
            LOG.log(Level.INFO, "outputObject of resSlope is {0}", resSlope.toString());

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

            IRasterLayer slope = (IRasterLayer) resSlope;
            LOG.log(Level.INFO, "Slope mean is {0}", slope.getMeanValue());

            ret = new GridCoverage2D("slopes", (GridCoverage2D) slope.getBaseDataObject());
            LOG.log(Level.INFO, "ret is {0}", ret.getPropertyNames().toString());
            
        } catch (GeoAlgorithmExecutionException ex) {
            
            LOG.log(Level.SEVERE, "An exception in the GeoAlgorithm has been thrown {0}", ex.getStackTrace());
            
        } catch (Exception e) {
            
            LOG.log(Level.SEVERE, "A generic exception has been thrown {0}", e.getStackTrace());
            
        }
        return ret;
    }
    
//    private GridCoverage2D toMeters (GridCoverage2D gc) throws FactoryException {
//
//        //GridGeometry gridgeom = new GridGeometry(gridRange, gridToCRS, crs);
//        //GridCoverage2D covtransformed = (GridCoverage2D) Operations.DEFAULT.resample(gc,gridgeom);
//        //int utm_zone = 10; 
//        //CRSAuthorityFactory f = FactoryFinder.getCRSAuthorityFactory("EPSG", null); 
//        //crs = f.createCoordinateReferenceSystem("EPSG:" + (32600 + utm_zone));  
//        
//        ProjectedCRS crs = new ProjectedCRS(DefaultProjectedCRS());
//        // Reproject to the default WGS84 CRS
//        
//        final ParameterValueGroup param = PROCESSOR.getOperation("Resample").getParameters();
//        param.parameter("Source").setValue(gc);
//        param.parameter("CoordinateReferenceSystem").setValue(DefaultProjectedCRS);
//        GridCoverage2D cov = (GridCoverage2D) PROCESSOR.doOperation(param);
//    
//        return cov;
//    }
    
    private static String getValue(GridCoverage2D grid, double x, double y) throws Exception {
 
        GridGeometry2D gg = grid.getGridGeometry();
 
        DirectPosition2D posWorld = new DirectPosition2D(x,y);
        GridCoordinates2D posGrid = gg.worldToGrid(posWorld);
 
        // envelope is the size in the target projection
        //double[] pixel=new double[1];
        //double[] data = gridData.getPixel(posGrid.x, posGrid.y, pixel);
        //return data[0];
        return grid.getDebugString(posWorld);
    }
    
}
