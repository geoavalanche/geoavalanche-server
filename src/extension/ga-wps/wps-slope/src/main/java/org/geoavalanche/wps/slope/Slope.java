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
import es.unex.sextante.outputs.FileOutputChannel;
import es.unex.sextante.outputs.Output;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.coverage.grid.io.imageio.GeoToolsWriteParams;
import org.geotools.coverage.processing.CoverageProcessor;
import org.geotools.factory.Hints;
import org.geotools.gce.geotiff.GeoTiffFormat;
import org.geotools.gce.geotiff.GeoTiffWriteParams;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.factory.StaticMethodsProcessFactory;
import org.geotools.text.Text;
import org.opengis.coverage.grid.GridCoverageWriter;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 * Slope
 *
 */
public class Slope extends StaticMethodsProcessFactory<Slope> {
    
    private static final Logger LOG = Logger.getLogger(Slope.class.getName());
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
    private static int method = SlopeAlgorithm.METHOD_ZEVENBERGEN;
    private static int unit = SlopeAlgorithm.UNITS_DEGREES;
    
    public Slope() {
        super(Text.text("GeoAvalanche"), GEOAVALANCHE_NAMESPACE, Slope.class);
    }
    
    @DescribeProcess(title = "Slope", description = "Calculate slopes in a shape")
    @DescribeResult(description = "Raster result with slopes")
    public static GridCoverage2D Slope (
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
        ParameterValueGroup param = PROCESSOR.getOperation("CoverageCrop").getParameters();
        param.parameter("Source").setValue(dem);
        param.parameter("Envelope").setValue(bounds);
        param.parameter("ROI").setValue(roi);

        //return (GridCoverage2D) PROCESSOR.doOperation(param);
        
        GridCoverage2D cropped = (GridCoverage2D) PROCESSOR.doOperation(param);
        LOG.info("cropped Coverage="+cropped);
        
        //Write coverage to file /tmp/Slope/xxx.tiff
        //final File writedir = new File(new StringBuilder("/tmp").append(File.separatorChar).append(Slope.class.getSimpleName()).toString());
        Path writedir = Paths.get(new StringBuilder("/tmp").append(File.separatorChar).append(Slope.class.getSimpleName()).toString());
        LOG.info("write directory="+writedir.toString());
        //Care to create directory
        //LOG.info("writedir is a Directory? "+writedir.isDirectory());
        //LOG.info("writedir exists? "+writedir.isDirectory());
        //if (!writedir.exists()) {
        if (!Files.exists(writedir)) {
            Files.createDirectories(writedir);
        }
        //final GeoTiffFormat format = new GeoTiffFormat();
        
        //final GeoTiffWriteParams wp = new GeoTiffWriteParams();
        //wp.setCompressionMode(GeoTiffWriteParams.MODE_EXPLICIT);
        //wp.setCompressionType("LZW");
        //wp.setTilingMode(GeoToolsWriteParams.MODE_EXPLICIT);
        //wp.setTiling(512, 512);
        
        //final ParameterValueGroup writerParams = format.getWriteParameters();
        //writerParams.parameter(AbstractGridFormat.GEOTOOLS_WRITE_PARAMS.getName().toString())
        //            .setValue(wp);
        
        //String cropFileName = new StringBuilder(writedir.getAbsolutePath()).append(File.separatorChar).append(cropped.getName().toString()).append(".tiff").toString();
        String cropFileName = new StringBuilder(writedir.toAbsolutePath().toString()).append(File.separatorChar).append(cropped.getName().toString()).append(".tiff").toString();
        LOG.info("crop filename="+cropFileName);
        File writeFile = new File(cropFileName);
        LOG.info("write file="+writeFile.toString());
        //final GridCoverageWriter writer = format.getWriter(writeFile, new Hints(Hints.CRS, cropped.getCoordinateReferenceSystem()));
        
        //GeoTiffWriter writer = new GeoTiffWriter(cropped, new Hints(Hints.CRS, cropped.getCoordinateReferenceSystem()));
//        try {
//            writer.write(cropped, (GeneralParameterValue[]) writerParams.values().toArray(new GeneralParameterValue[1]));
//        } catch (IOException e) {
//            e.printStackTrace();
//        } finally {
//            try {
//                writer.dispose();
//            } catch (Throwable e) {
//               e.printStackTrace(); 
//            }
//        }
        
        writeToGeotiff(cropped, cropFileName);
        /** Add a static coverage */
        
        //static file
        //GridCoverage2D lCov = getLocalCoverage("result.tiff");
        
        //
        GridCoverage2D lCov = getLocalCoverage(writeFile.getName());
        LOG.info("lcov="+lCov);
        
        /*
         * Initialize the library.
         * This will load all the algorithms and resource strings.
         * Since no language code is passed, default language(en)
         * will be used
         */
        Sextante.initialize();

        GTRasterLayer raster = new GTRasterLayer();
        //raster.create(cropCov);
        raster.create(lCov);
        LOG.info("raster = "+raster);        
        IRasterLayer slope = getSlope(raster,method,unit,raster.getLayerGridExtent());   
        GridCoverage2D ret = (GridCoverage2D)slope.getBaseDataObject();
        LOG.info("ret="+ret);            

        return ret;
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
        alg.execute(null, outputFactory);

        /*
         * Now the result can be taken from the output container
         */
        IRasterLayer slope = (IRasterLayer) out.getOutputObject();

        return slope;

    }
    
    static GridCoverage2D getLocalCoverage(String filename) throws Exception {

        File file = new File("/tmp"+File.separatorChar+Slope.class.getSimpleName()+File.separatorChar+filename);
        LOG.info("geotiff file to read "+file.toString());
        AbstractGridFormat format = GridFormatFinder.findFormat(file);
        GridCoverage2DReader reader = format.getReader(file);
        GridCoverage2D coverage = reader.read(null);
        LOG.info("coverage=" + coverage);
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
}
