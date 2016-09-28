package org.geoavalanche.wps.ateinorm;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import es.unex.sextante.core.AnalysisExtent;
import es.unex.sextante.core.OutputFactory;
import es.unex.sextante.core.OutputObjectsSet;
import es.unex.sextante.core.ParametersSet;
import es.unex.sextante.core.Sextante;
import es.unex.sextante.dataObjects.IRasterLayer;
import es.unex.sextante.exceptions.GeoAlgorithmExecutionException;
import es.unex.sextante.exceptions.WrongOutputIDException;
import es.unex.sextante.exceptions.WrongParameterIDException;
import es.unex.sextante.gridStatistics.multiGridMajority.MultiGridMajorityAlgorithm;
import es.unex.sextante.gridTools.gridBasicStats.GridBasicStatsAlgorithm;
import es.unex.sextante.morphometry.aspect.AspectAlgorithm;
import es.unex.sextante.morphometry.curvatures.CurvaturesAlgorithm;
import es.unex.sextante.morphometry.slope.SlopeAlgorithm;
import org.geoavalanche.alg.avalanche.AvalancheTerrainExposureAlgorithm;
import es.unex.sextante.outputs.Output;
import es.unex.sextante.outputs.OutputNumericalValue;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.wps.sextante.GTOutputFactory;
import org.geoserver.wps.sextante.GTRasterLayer;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.coverage.processing.CoverageProcessor;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.gce.geotiff.GeoTiffFormat;
import org.geotools.gce.geotiff.GeoTiffWriteParams;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.factory.StaticMethodsProcessFactory;
import org.geotools.referencing.CRS;
import org.geotools.text.Text;
import org.geotools.util.logging.Logging;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

/**
 * ATEINorm
 *
 */
public class ATEINorm extends StaticMethodsProcessFactory<ATEINorm> {
    
    private static final Logger LOG = Logging.getLogger(ATEINorm.class.getName());
    private static final CoverageProcessor PROCESSOR = CoverageProcessor.getInstance();
    public static final String GEOAVALANCHE_NAMESPACE = "geoavalanche";

    /*
     * The output factory to use when calling geoalgorithms
     * This tells the algorithm how to create new data objects (layers
     * and tables)
     * The GTOutputFactory creates objects based on geotools
     * data objects (DataStore and GridCoverage)
     */
    private static OutputFactory outputFactory = new GTOutputFactory();
    
    private static int methodS  = SlopeAlgorithm.METHOD_ZEVENBERGEN;
    private static int unitS    = SlopeAlgorithm.UNITS_DEGREES;
    
    private static int methodA = AspectAlgorithm.METHOD_ZEVENBERGEN;
    private static int unitA   = AspectAlgorithm.UNITS_DEGREES;
    
    private static String methodC = "Fit_2_Degree_Polynom__Zevenbergen_&_Thorne_1987";
    private static String result = CurvaturesAlgorithm.CLASS;
    
    private static String slope = AvalancheTerrainExposureAlgorithm.SLOPE;
    private static String aspect = AvalancheTerrainExposureAlgorithm.ASPECT;
    private static String curvature = AvalancheTerrainExposureAlgorithm.CURVATURE;
    private static String landclass = AvalancheTerrainExposureAlgorithm.LANDCLASS;
    private static String atei = AvalancheTerrainExposureAlgorithm.ATEI;
    
    private static String majorityValue = MultiGridMajorityAlgorithm.RESULT;
    
    private static String meanSquaredValue = GridBasicStatsAlgorithm.MEAN_SQUARED;
    private static String meanValue = GridBasicStatsAlgorithm.MEAN;
    
    public ATEINorm() {
        super(Text.text("GeoAvalanche"), GEOAVALANCHE_NAMESPACE, ATEINorm.class);
    }
    
    @DescribeProcess(title = "ATEINorm", description = "Calculate Avalanche Terrain Exposure Majority Value Index in a feature collection")
    @DescribeResult(description = "Shape result with ATEINorm")
    public static SimpleFeatureCollection ATEINorm (
            
            @DescribeParameter(name = "dem", description = "DEM coverage") GridCoverage2D dem,
            @DescribeParameter(name = "clc", description = "Copernicus Land Cover") GridCoverage2D clc,
            @DescribeParameter(name = "FeatureCollection", description = "FeatureCollection") SimpleFeatureCollection featureCollection,
            @DescribeParameter(name = "sourceCRS", description = "sourceCRS", min=0, max=1) CoordinateReferenceSystem sourceCRS
    ) throws Exception {
        try {
            LOG.info("step 1");
            
            boolean lenient = true;
            CoordinateReferenceSystem sourceCRS_ = null;
            MathTransform transform3035 = null;
                    
            if (sourceCRS == null) {
                //sourceCrs = featureCollection.getBounds().getCoordinateReferenceSystem(); doesn't work, returns ever null
                sourceCRS_ = CRS.decode("EPSG:4326");
            }
            else {
                sourceCRS_ = sourceCRS;
            }
            LOG.info("source CRS of feature collection ="+sourceCRS_);
            
            try {
                transform3035 = CRS.findMathTransform(sourceCRS_, CRS.decode("EPSG:3035"), lenient);
            } catch (Exception e) {
                LOG.severe("Error with source CRS obtained from feature collection with exception "+e);
            }
            
            List<SimpleFeature> featuresList = new ArrayList<SimpleFeature>();
            SimpleFeatureBuilder fb = getSimpleFeatureBuilder(featureCollection);

            LOG.info("step 2");
            
            SimpleFeatureIterator itr = featureCollection.features();
            Level _level = LOG.getLevel();
            while (itr.hasNext()) {
                
                SimpleFeature feature = itr.next();
                fb.reset();
                for (Property p : feature.getProperties()) {
                    fb.set(p.getName().getLocalPart(), p.getValue());
                }
                Geometry theGeometry = (Geometry) feature.getAttribute("the_geom");
                if (theGeometry == null) {
                    theGeometry = (Geometry) feature.getAttribute("geometry");
                }
                if (theGeometry != null) {
                    
                    Geometry procGeom = null;
                    
                    if (sourceCRS_!=CRS.decode("EPSG:3035")) {
                        procGeom = JTS.transform(theGeometry, transform3035);
                    }    
                    else {
                        procGeom = theGeometry;
                    }
                    String relAtei = Double.toString(getAteiNorm(procGeom, dem, clc, true)); 
                    LOG.info("returned atei ="+relAtei);
                    fb.set("atei", relAtei);
                }

                featuresList.add(fb.buildFeature(feature.getID()));
                LOG.setLevel(Level.OFF);
            }
            LOG.setLevel(_level);
            SimpleFeatureCollection ret = new ListFeatureCollection(fb.getFeatureType(), featuresList);
            LOG.info("step 2 ... done ");
            LOG.info("nrec = " + ret.size());
            itr.close();
            return ret;
        } catch (Exception e) {
            e.printStackTrace();
            return featureCollection;
        }
    }
    
    private static double getAteiNorm(Geometry thegeom, GridCoverage2D globdem, GridCoverage2D globclc, boolean withRoi) throws GeoAlgorithmExecutionException, Exception {
        
        // get the bounds
        
        GeneralEnvelope bounds = new GeneralEnvelope(new ReferencedEnvelope(thegeom.getEnvelopeInternal(), globdem.getCoordinateReferenceSystem()));
        LOG.info("bounds=" + bounds);
        
        // get a polygon shape for Atei if receiving multipolygon
        Polygon roi = null;
        if (withRoi) {            
            if (thegeom instanceof Polygon) {
                roi = (Polygon)thegeom;
            } else if (thegeom instanceof MultiPolygon) {
                MultiPolygon mp = (MultiPolygon) thegeom;
                if (mp.getNumGeometries()>=1) {
                    roi = (Polygon)mp.getGeometryN(0);
                }
            }
            LOG.info("roi=" + roi);
        }
        
        //@TODO Do cropping and writing/reading to/from file concurrently
        
        // performing the crop of dem
        final ParameterValueGroup demParam = PROCESSOR.getOperation("CoverageCrop").getParameters();
        demParam.parameter("Source").setValue(globdem);
        demParam.parameter("Envelope").setValue(bounds);
        demParam.parameter("ROI").setValue(roi);

        GridCoverage2D croppedDEM = (GridCoverage2D) PROCESSOR.doOperation(demParam);
        LOG.info("cropped DEM coverage="+croppedDEM);
        
        // performing the crop of clc
        final ParameterValueGroup clcParam = PROCESSOR.getOperation("CoverageCrop").getParameters();
        clcParam.parameter("Source").setValue(globclc);
        clcParam.parameter("Envelope").setValue(bounds);
        clcParam.parameter("ROI").setValue(roi);

        GridCoverage2D croppedCLC = (GridCoverage2D) PROCESSOR.doOperation(clcParam);
        LOG.info("cropped DEM coverage="+croppedCLC);
        
        //Write DEM coverage to file in temp directory /tmp/ATEINorm/sample_cropped.tiff
        String tmpDir = System.getProperty("java.io.tmpdir");
        Path writedir = Paths.get(new StringBuilder(tmpDir).append(File.separatorChar).append(ATEINorm.class.getSimpleName()).toString());
        LOG.info("write directory="+writedir.toString());
        //Care to create directory
        //LOG.info("writedir is a Directory? "+writedir.isDirectory());
        //LOG.info("writedir exists? "+writedir.isDirectory());
        //if (!writedir.exists()) {
        if (!Files.exists(writedir)) {
        
            //writedir.mkdirs();
            try {
                Files.createDirectories(writedir);
            } catch (IOException e) {
                //fail to create directory
                e.printStackTrace();
            }
            
        }
        
        
        String cropDemFileName = new StringBuilder(writedir.toAbsolutePath().toString()).append(File.separatorChar).append(croppedDEM.getName().toString()).append("dem_"+UUID.randomUUID().toString()+".tiff").toString();
        LOG.info("crop DEM filename="+cropDemFileName);
        final File writeDEMFile = new File(cropDemFileName);
        LOG.info("write DEM file="+writeDEMFile.toString());
        
        String cropClcFileName = new StringBuilder(writedir.toAbsolutePath().toString()).append(File.separatorChar).append(croppedCLC.getName().toString()).append("clc_"+UUID.randomUUID().toString()+".tiff").toString();
        LOG.info("crop CLC filename="+cropClcFileName);
        final File writeCLCFile = new File(cropClcFileName);
        LOG.info("write CLC file="+writeCLCFile.toString());
        
        //write to filesystem
        writeToGeotiff(croppedDEM, cropDemFileName);
        writeToGeotiff(croppedCLC, cropClcFileName);
        
        //read from filesystem
        GridCoverage2D lDEMCov = getLocalCoverage(writeDEMFile.getName());
        LOG.info("lDEMcov="+lDEMCov);
        
        GridCoverage2D lCLCCov = getLocalCoverage(writeCLCFile.getName());
        LOG.info("lCLCcov="+lCLCCov);
        
        /*
         * Initialize the library.
         * This will load all the algorithms and resource strings.
         * Since no language code is passed, default language(en)
         * will be used
         */
        Sextante.initialize();

        GTRasterLayer rasterDEM = new GTRasterLayer();
        rasterDEM.create(lDEMCov);
        LOG.info("raster DEM = "+rasterDEM);
        
        GTRasterLayer rasterCLC = new GTRasterLayer();
        rasterCLC.create(lCLCCov);
        LOG.info("raster CLC = "+rasterCLC);
        
        IRasterLayer slope = getSlope(rasterDEM,methodS,unitS,rasterDEM.getLayerGridExtent());
        IRasterLayer aspect = getAspect(rasterDEM,methodA,unitA,rasterDEM.getLayerGridExtent());
        IRasterLayer curvature = getCurvature(rasterDEM,methodC,rasterDEM.getLayerGridExtent());
        IRasterLayer landcover = (IRasterLayer) rasterCLC;
        
        double ateiMean = getATEIMean(slope,aspect,curvature,landcover,slope.getLayerGridExtent());
        
        //IRasterLayer ateiMGrid = getATEIMajority(slope,aspect,curvature,landcover,slope.getLayerGridExtent());               

        //Process with Extrema for the highest
        /* GridCoverage2D ateiMajorityGC = (GridCoverage2D) ateiMGrid.getBaseDataObject();
        LOG.info("returned atei majority grid layer="+ateiMajorityGC); 
        
        ParameterValueGroup paramsExtrema = PROCESSOR.getOperation("Extrema").getParameters();
        paramsExtrema.parameter("Source").setValue(ateiMajorityGC);        
        GridCoverage2D result = (GridCoverage2D) PROCESSOR.doOperation(paramsExtrema, null);
        double[] ateiHighestValue = (double[]) result.getProperty("maximum");
        for (int x = 0; x < ateiHighestValue.length; x++) {
            LOG.info("maximum[" + x + "]=" + ateiHighestValue[x]);
        }
        LOG.info("ateiMajorValue="+ateiHighestValue);
        
        return ateiHighestValue[0]; */
        
        return ateiMean;
        
    }
    
    static SimpleFeatureBuilder getSimpleFeatureBuilder(SimpleFeatureCollection featureCollection) {
        Map<String, Class> theProperties = new HashMap();
        SimpleFeatureIterator itr = featureCollection.features();
        while (itr.hasNext()) {
            SimpleFeature feature = itr.next();
            for (Property p : feature.getProperties()) {
                theProperties.put(p.getName().getLocalPart(), p.getType().getBinding());
            }
        }
        theProperties.put("atei", String.class);

        SimpleFeatureTypeBuilder tb = new SimpleFeatureTypeBuilder();
        tb.setName(featureCollection.getSchema().getName());
        tb.setCRS(featureCollection.getSchema().getCoordinateReferenceSystem());
        for (Map.Entry<String, Class> entry : theProperties.entrySet()) {
            tb.add(entry.getKey(), entry.getValue());
        }
        SimpleFeatureType schema = tb.buildFeatureType();
        SimpleFeatureBuilder fb = new SimpleFeatureBuilder(schema);
        return fb;
    }
    
    /**
     * Returns a slope layer created from the passed DEM
     *
     * @param dem the DEM
     * @return a slope layer
     * @throws GeoAlgorithmExecutionException
     */
    private static IRasterLayer getSlope(IRasterLayer dem, int method, int unit, AnalysisExtent ext)
            throws GeoAlgorithmExecutionException {

        /*
         * Instantiate the SlopeAlgorithm class
         */
        SlopeAlgorithm alg = new SlopeAlgorithm();
        alg.setAnalysisExtent(ext);

        /*
         * The first thing we have to do is to set up the input parameters
         */
        ParametersSet params = alg.getParameters();
        params.getParameter(SlopeAlgorithm.DEM).setParameterValue(dem);

        //Zevenberger & Thorne method
        params.getParameter(SlopeAlgorithm.METHOD).setParameterValue(method);

        //Resulting values in degree
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
        //@TODO Maybe it is better to monitoring the task and raise exceptions in try..catch
        alg.execute(null, outputFactory); 

        /*
         * Now the result can be taken from the output container
         */
        IRasterLayer slope = (IRasterLayer) out.getOutputObject();

        return slope;

    }
    
    /**
     * Returns an aspect layer created from the passed DEM
     *
     * @param dem the DEM
     * @return a aspect layer
     * @throws GeoAlgorithmExecutionException
     */
    private static IRasterLayer getAspect(IRasterLayer dem, int method, int unit, AnalysisExtent ext) 
            throws GeoAlgorithmExecutionException {
        
        /*
         * Instantiate the AspectAlgorithm class
         */
        AspectAlgorithm alg = new AspectAlgorithm();
        alg.setAnalysisExtent(ext);

        /*
         * The first thing we have to do is to set up the input parameters
         */
        ParametersSet params = alg.getParameters();
        params.getParameter(AspectAlgorithm.DEM).setParameterValue(dem);

        //Zevenberger & Thorne method
        params.getParameter(AspectAlgorithm.METHOD).setParameterValue(method);
        
        //Resulting values in degree
        params.getParameter(AspectAlgorithm.UNITS).setParameterValue(unit);

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
        Output out = outputs.getOutput(AspectAlgorithm.ASPECT);

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
        //@TODO Maybe it is better to monitoring the task and raise exceptions in try..catch
        alg.execute(null, outputFactory); 

        /*
         * Now the result can be taken from the output container
         */
        IRasterLayer aspect = (IRasterLayer) out.getOutputObject();

        return aspect;
        
    }
    
    /**
     * Returns a curvature layer created from the passed DEM
     *
     * @param dem the DEM
     * @return a curvature layer
     * @throws GeoAlgorithmExecutionException
     */
    private static IRasterLayer getCurvature(IRasterLayer dem, String method, AnalysisExtent ext) 
            throws GeoAlgorithmExecutionException {
        
        /*
         * Instantiate the CurvatureAlgorithm class
         */
        CurvaturesAlgorithm alg = new CurvaturesAlgorithm();
        alg.setAnalysisExtent(ext);

        /*
         * The first thing we have to do is to set up the input parameters
         */
        ParametersSet params = alg.getParameters();
        params.getParameter(CurvaturesAlgorithm.DEM).setParameterValue(dem);

        //Zevenberger & Thorne method
        params.getParameter(CurvaturesAlgorithm.METHOD).setParameterValue(method);
        

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
        Output out = outputs.getOutput(result);

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
        //@TODO Maybe it is better to monitoring the task and raise exceptions in try..catch
        alg.execute(null, outputFactory); 

        /*
         * Now the result can be taken from the output container
         */
        IRasterLayer curvature = (IRasterLayer) out.getOutputObject();

        return curvature;
        
    }
    
    /**
     * Returns an Avalanche Terrain Exposure layer created from the passed Slope,Aspect,Curvature,LandCover
     *
     * @param slope the Slope
     * @param aspect the Aspect
     * @param curvature the Curvature
     * @param landcover the LandCover
     * @param ext the Extent of the calculation
     * @return an atei layer with the majority
     * @throws GeoAlgorithmExecutionException
     */
    private static double getATEIMean(IRasterLayer slope, IRasterLayer aspect, IRasterLayer curvature, IRasterLayer landcover, AnalysisExtent ext) 
            throws GeoAlgorithmExecutionException {
        
        /*
         * Instantiate the AvalancheTerrainExposureAlgorithm class
         */
        AvalancheTerrainExposureAlgorithm alg = new AvalancheTerrainExposureAlgorithm();
        alg.setAnalysisExtent(ext);
        
        /*
         * The first thing we have to do is to set up the input parameters
         */
        ParametersSet params = alg.getParameters();
        
        //slope input
        params.getParameter(ATEINorm.slope).setParameterValue(slope);

        //aspect input
        params.getParameter(ATEINorm.aspect).setParameterValue(aspect);
        
        //curvature input
        params.getParameter(ATEINorm.curvature).setParameterValue(curvature);
        
        //landcover input
        params.getParameter(landclass).setParameterValue(landcover);
        
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
        Output out = outputs.getOutput(atei);
        
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
        //@TODO Maybe it is better to monitoring the task and raise exceptions in try..catch
        alg.execute(null, outputFactory); 

        /*
         * Now the result can be taken from the output container
         */
        IRasterLayer atei = (IRasterLayer) out.getOutputObject();
        
        //Process with Mean of values
        double ateiMean = getMean(atei,atei.getLayerGridExtent());
        
        return ateiMean;
        
    }
    
    private static double getMean(IRasterLayer raster, AnalysisExtent ext) 
            throws WrongParameterIDException, WrongOutputIDException, GeoAlgorithmExecutionException {
        
        /*Calculate the majority of values from the pixels of the grid*/
        
        /*
         * Instantiate the GridBasicStatsAlgorithm class
         */
        GridBasicStatsAlgorithm alg = new GridBasicStatsAlgorithm();
        alg.setAnalysisExtent(ext);
        
        /*
         * The first thing we have to do is to set up the input parameters
         */
        ParametersSet params = alg.getParameters();
        params = alg.getParameters();
        
        //raster input
        params.getParameter(GridBasicStatsAlgorithm.INPUT).setParameterValue(raster);
        
        /*
         *  This algorithm will generate a new double value.
         * We can select "where" to put the result. To do this, we
         * retrieve the output container and set the output channel,
         * which contains information about the destiny of the resulting
         * data.
         */
        OutputObjectsSet outputs = alg.getOutputObjects();
        OutputNumericalValue out = (OutputNumericalValue) outputs.getOutput(meanValue);

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
        //@TODO Maybe it is better to monitoring the task and raise exceptions in try..catch
        alg.execute(null, outputFactory); 
        
        /*
         * Now the result can be taken from the output container
         */
        double mean = (double) out.getOutputObject();
        
        LOG.info("The mean value of values="+mean);
    
        return mean;
        
    }
    
    /**
     * Returns an Avalanche Terrain Exposure layer created from the passed Slope,Aspect,Curvature,LandCover
     *
     * @param slope the Slope
     * @param aspect the Aspect
     * @param curvature the Curvature
     * @param landcover the LandCover
     * @param ext the Extent of the calculation
     * @return an atei layer with the majority
     * @throws GeoAlgorithmExecutionException
     */
    private static IRasterLayer getATEIMajority(IRasterLayer slope, IRasterLayer aspect, IRasterLayer curvature, IRasterLayer landcover, AnalysisExtent ext) 
            throws GeoAlgorithmExecutionException {
        
        /*
         * Instantiate the AvalancheTerrainExposureAlgorithm class
         */
        AvalancheTerrainExposureAlgorithm alg = new AvalancheTerrainExposureAlgorithm();
        alg.setAnalysisExtent(ext);
        
        /*
         * The first thing we have to do is to set up the input parameters
         */
        ParametersSet params = alg.getParameters();
        
        //slope input
        params.getParameter(ATEINorm.slope).setParameterValue(slope);

        //aspect input
        params.getParameter(ATEINorm.aspect).setParameterValue(aspect);
        
        //curvature input
        params.getParameter(ATEINorm.curvature).setParameterValue(curvature);
        
        //landcover input
        params.getParameter(landclass).setParameterValue(landcover);
        
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
        Output out = outputs.getOutput(atei);
        
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
        //@TODO Maybe it is better to monitoring the task and raise exceptions in try..catch
        alg.execute(null, outputFactory); 

        /*
         * Now the result can be taken from the output container
         */
        IRasterLayer atei = (IRasterLayer) out.getOutputObject();
        
        //Process with Majority values
        IRasterLayer ateiMajorityGrid = getGridMajority(atei,atei.getLayerGridExtent());
        
        return ateiMajorityGrid;
        
    }
    
    private static IRasterLayer getGridMajority(IRasterLayer raster, AnalysisExtent ext) 
            throws WrongParameterIDException, WrongOutputIDException, GeoAlgorithmExecutionException {
        
        /*Calculate the majority of values from the pixels of the grid*/
        
        /*
         * Instantiate the MultiGridMajorityAlgorithm class
         */
        MultiGridMajorityAlgorithm alg = new MultiGridMajorityAlgorithm();
        alg.setAnalysisExtent(ext);
        
        /*
         * The first thing we have to do is to set up the input parameters
         */
        ParametersSet params = alg.getParameters();
        params = alg.getParameters();
        
        //raster input
        params.getParameter(MultiGridMajorityAlgorithm.INPUT).setParameterValue(raster);
        
        /*
         *  This algorithm will generate a new double value.
         * We can select "where" to put the result. To do this, we
         * retrieve the output container and set the output channel,
         * which contains information about the destiny of the resulting
         * data.
         */
        OutputObjectsSet outputs = alg.getOutputObjects();
        Output out = outputs.getOutput(majorityValue);

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
        //@TODO Maybe it is better to monitoring the task and raise exceptions in try..catch
        alg.execute(null, outputFactory); 
        
        /*
         * Now the result can be taken from the output container
         */
        IRasterLayer majority = (IRasterLayer) out.getOutputObject();
        
        LOG.info("The majority value of values="+majority);
    
        return majority;
        
    }
    
    static GridCoverage2D getLocalCoverage(String filename) throws Exception {

        String tmpDir = System.getProperty("java.io.tmpdir");
        File file = new File(tmpDir+File.separatorChar+ATEINorm.class.getSimpleName()+File.separatorChar+filename);
        LOG.info("geotiff file to read "+file.toString());
        AbstractGridFormat format = GridFormatFinder.findFormat(file);
        GridCoverage2DReader reader = format.getReader(file);
        GridCoverage2D coverage = reader.read(null);
        LOG.info("retrieved coverage=" + coverage);
        return coverage;
    }
    
    //write to geotiff
    static void writeToGeotiff(GridCoverage2D cov, String fileName) {
        try {
            GeoTiffWriteParams wp = new GeoTiffWriteParams();
            wp.setCompressionMode(GeoTiffWriteParams.MODE_EXPLICIT);
            wp.setCompressionType("LZW");
            ParameterValueGroup params = new GeoTiffFormat().getWriteParameters();
            params.parameter(AbstractGridFormat.GEOTOOLS_WRITE_PARAMS.getName().toString()).setValue(wp);
            File wfile = new File(fileName);
            LOG.info("wfile="+wfile);
            new GeoTiffWriter(wfile).write(cov, (GeneralParameterValue[]) params.values().toArray(new GeneralParameterValue[1]));
        } catch (Exception e) {
            LOG.severe("exception while writing geotiff.");
            e.printStackTrace();
        }
    }
    
    static List<SimpleFeature> transform(List<SimpleFeature> featuresList, CoordinateReferenceSystem sourceCRS, CoordinateReferenceSystem targetCRS) throws Exception {
        if (sourceCRS == null | targetCRS == null | sourceCRS==targetCRS) return featuresList;
        List<SimpleFeature> ret = new ArrayList();
        boolean lenient = true;
        MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, lenient);
        for (SimpleFeature feature : featuresList) {
            if (feature.getDefaultGeometry()!=null) {
                feature.setDefaultGeometry(JTS.transform((Geometry)feature.getDefaultGeometry(), transform));
            } else {
                feature.setAttribute("the_geom", JTS.transform((Geometry)feature.getAttribute("the_geom"), transform));
            }
            ret.add(feature);
        }
        return ret;
    }
    
}
