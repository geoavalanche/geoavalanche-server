package org.geoavalanche.wps.snowpack;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
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
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.factory.StaticMethodsProcessFactory;
import org.geotools.text.Text;
import org.opengis.coverage.grid.GridCoverageWriter;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.geometry.Envelope;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.parameter.ParameterValueGroup;

public class SnowPack extends StaticMethodsProcessFactory<SnowPack> {

    private static final Logger LOG = Logger.getLogger(SnowPack.class.getName());
    private static final CoverageProcessor PROCESSOR = CoverageProcessor.getInstance();
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    private static final String LOCAL_REPOSITORY = System.getProperty("java.io.tmpdir")+File.separator+"snowpack"+File.separator;

    public SnowPack() {
        super(Text.text("GeoAvalanche"), "geoavalanche", SnowPack.class);
    }

    @DescribeProcess(title = "SnowPack", description = "Calculate snowmelt and snowcover for all geometries in the collection")
    @DescribeResult(description = "FeatureCollection")
    public static SimpleFeatureCollection SnowPack(
            @DescribeParameter(name = "FeatureCollection", description = "FeatureCollection (with crs EPSG:4326)", min = 1, max = 1) SimpleFeatureCollection featureCollection
    ) throws Exception {
        try {
            LOG.info("step 1");
            
            List<SimpleFeature> featuresList = new ArrayList<SimpleFeature>();
            SimpleFeatureBuilder fb = getSimpleFeatureBuilder(featureCollection);

            ArrayList<String> daily_SWE_PanEuropean_Microwave = getTimeData("daily_SWE_PanEuropean_Microwave");
            String daily_FSC_PanEuropean_Optical = getLastTimeData("daily_FSC_PanEuropean_Optical",10);
            String daily_FSC_Alps_Optical = getLastTimeData("daily_FSC_Alps_Optical",10);
            String daily_FSC_Baltic_Optical = getLastTimeData("daily_FSC_Baltic_Optical",10);
            String multitemp_SCAW_Alps_Radar = getLastTimeData("multitemp_SCAW_Alps_Radar",null);
            String daily_SCAW_Scandinavia_Radar = getLastTimeData("daily_SCAW_Scandinavia_Radar",null);
            LOG.info("step 1 ... done");

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
                    fb.set("swe", swe(theGeometry, daily_SWE_PanEuropean_Microwave));
                    String fsc1 = fsc_noroi(theGeometry, daily_FSC_PanEuropean_Optical);
                    String fsc2 = fsc(theGeometry, daily_FSC_Alps_Optical);
                    String fsc3 = fsc(theGeometry, daily_FSC_Baltic_Optical);
                    fb.set("fsc", fsc1+","+fsc2+","+fsc3);
                    String scaw1 = scaw(theGeometry, multitemp_SCAW_Alps_Radar);
                    String scaw2 = scaw(theGeometry, daily_SCAW_Scandinavia_Radar);
                    fb.set("scaw", scaw1+","+scaw2);
                }

                featuresList.add(fb.buildFeature(feature.getID()));
                LOG.setLevel(Level.OFF);
            }
            LOG.setLevel(_level);
            SimpleFeatureCollection ret = new ListFeatureCollection(fb.getFeatureType(), featuresList);
            LOG.info("step 2 ... done ");
            LOG.info("nrec = " + ret.size());
            return ret;
        } catch (Exception e) {
            return featureCollection;
        }
    }

    static Geometry getPixel(Geometry theGeometry, String filename) throws Exception {
        File file = new File(LOCAL_REPOSITORY + filename);
        AbstractGridFormat format = GridFormatFinder.findFormat(file);
        GridCoverage2DReader reader = format.getReader(file);
        GridCoverage2D coverage = reader.read(null);
        LOG.info("coverage=" + coverage);

        LOG.info("geometry=" + theGeometry);

        GeneralEnvelope bounds = new GeneralEnvelope(new ReferencedEnvelope(theGeometry.getEnvelopeInternal(), coverage.getCoordinateReferenceSystem()));
        LOG.info("bounds=" + bounds);

        ParameterValueGroup paramCoverageCrop = PROCESSOR.getOperation("CoverageCrop").getParameters();
        paramCoverageCrop.parameter("Source").setValue(coverage);
        paramCoverageCrop.parameter("Envelope").setValue(bounds);
        GridCoverage2D cropped = (GridCoverage2D) PROCESSOR.doOperation(paramCoverageCrop);
        LOG.info("cropped=" + cropped);

        Envelope e = cropped.getEnvelope();
        double[] uppercorner = e.getUpperCorner().getCoordinate();
        double[] lowercorner = e.getLowerCorner().getCoordinate();

        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
        Coordinate[] coords =  new Coordinate[] {
            new Coordinate(uppercorner[0], uppercorner[1]), 
            new Coordinate(uppercorner[0], lowercorner[1]), 
            new Coordinate(lowercorner[0], lowercorner[1]),
            new Coordinate(lowercorner[0], uppercorner[1]), 
            new Coordinate(uppercorner[0], uppercorner[1])
        };
        Polygon thePolygon = geometryFactory.createPolygon(coords);
        MultiPolygon theMultiPolygon = new MultiPolygon(new Polygon[]{thePolygon}, thePolygon.getFactory());
        return theMultiPolygon;      
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
        theProperties.put("swe", String.class);
        theProperties.put("fsc", String.class);
        theProperties.put("scaw", String.class);

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

    static String swe(Geometry theGeometry, ArrayList<String> filenames) throws Exception {
        String ret = null;
        for (String _filename : filenames) {
            if (ret == null) {
                ret = swe(theGeometry, _filename);
            } else {
                ret = ret+","+swe(theGeometry, _filename);
            }
        }
        if (ret == null) ret="null";
        return ret;
    }
    
    static String swe(Geometry theGeometry, String filename) throws Exception {
        try {
            LOG.info("swe, filename=" + filename);
            double swe = maximum(theGeometry, filename);
            LOG.info("swe (snow water equivalent) = " + swe);
            return "" + swe;
        } catch (Throwable e) {
            return "null";
        }
    }

    static String fsc(Geometry theGeometry, String filename) throws Exception {
        try {
            LOG.info("fsc, filename=" + filename);
            double fsc = maximum(theGeometry, filename);
            LOG.info("fsc (Fractional Snow Cover) = " + fsc);
            return "" + fsc;
        } catch (Throwable e) {
            return "null";
        }
    }

    static String fsc_noroi(Geometry theGeometry, String filename) throws Exception {
        try {
            LOG.info("fsc, filename=" + filename);
            double fsc = maximum(theGeometry, filename, false);
            LOG.info("fsc (Fractional Snow Cover) = " + fsc);
            return "" + fsc;
        } catch (Throwable e) {
            return "null";
        }
    }

    static String scaw(Geometry theGeometry, String filename) throws Exception {
        try {
            LOG.info("scaw, filename=" + filename);
            double scaw = maximum(theGeometry, filename);
            LOG.info("scaw = " + scaw);
            return "" + scaw;
        } catch (Throwable e) {
            return "null";
        }        
    }
    
    static double maximum(Geometry theGeometry, String filename) throws Exception {
        return maximum(theGeometry, filename, true);
    }

    static double maximum(Geometry theGeometry, String filename, boolean withRoi) throws Exception {
        if (filename==null) {
            throw new Exception("filename can not be null");            
        }
        File file = new File(LOCAL_REPOSITORY + filename);
        AbstractGridFormat format = GridFormatFinder.findFormat(file);
        GridCoverage2DReader reader = format.getReader(file);
        GridCoverage2D coverage = reader.read(null);
        LOG.info("coverage=" + coverage);

        LOG.info("geometry=" + theGeometry);

        GeneralEnvelope bounds = new GeneralEnvelope(new ReferencedEnvelope(theGeometry.getEnvelopeInternal(), coverage.getCoordinateReferenceSystem()));
        LOG.info("bounds=" + bounds);
        
        Polygon roi = null;
        if (withRoi) {            
            if (theGeometry instanceof Polygon) {
                roi = (Polygon)theGeometry;
            } else if (theGeometry instanceof MultiPolygon) {
                MultiPolygon mp = (MultiPolygon) theGeometry;
                if (mp.getNumGeometries()>=1) {
                    roi = (Polygon)mp.getGeometryN(0);
                }
            }
            LOG.info("roi=" + roi);
        }
        
        GridCoverage2D cropped = null;
        try {
            //Range nodata = RangeFactory.create(201.0, 300.0);
            ParameterValueGroup paramCoverageCrop = PROCESSOR.getOperation("CoverageCrop").getParameters();
            paramCoverageCrop.parameter("Source").setValue(coverage);
            paramCoverageCrop.parameter("Envelope").setValue(bounds);
            //paramCoverageCrop.parameter("NoData").setValue(nodata);
            if (roi!=null) { 
                paramCoverageCrop.parameter("ROI").setValue(roi); 
            }
            cropped = (GridCoverage2D) PROCESSOR.doOperation(paramCoverageCrop);
            LOG.info("cropped=" + cropped); 
            
        } catch(Exception e) {
            ParameterValueGroup paramCoverageCrop = PROCESSOR.getOperation("CoverageCrop").getParameters();
            paramCoverageCrop.parameter("Source").setValue(coverage);
            paramCoverageCrop.parameter("Envelope").setValue(bounds);
            cropped = (GridCoverage2D) PROCESSOR.doOperation(paramCoverageCrop);            
        }

        ParameterValueGroup paramsExtrema = PROCESSOR.getOperation("Extrema").getParameters();
        paramsExtrema.parameter("Source").setValue(cropped);        
        GridCoverage2D result = (GridCoverage2D) PROCESSOR.doOperation(paramsExtrema, null);
        double[] maximum = (double[]) result.getProperty("maximum");
        for (int x = 0; x < maximum.length; x++) {
            LOG.info("maximum[" + x + "]=" + maximum[x]);
        }
        
        //save(cropped, filename);
        
        return maximum[0];
    }
    
    static void save(GridCoverage2D sourceCoverage, String filename) throws Exception {
        FileOutputStream output = new FileOutputStream(LOCAL_REPOSITORY + "crop_"+filename);
        
        final GeoTiffFormat format = new GeoTiffFormat();
        final GeoTiffWriteParams wp = new GeoTiffWriteParams();
        wp.setCompressionMode(GeoTiffWriteParams.MODE_EXPLICIT);
        wp.setCompressionType("LZW");
        wp.setCompressionQuality(1.0F);
        //wp.setTilingMode(GeoToolsWriteParams.MODE_EXPLICIT);
        //wp.setTiling(256, 256);

        final ParameterValueGroup writerParams = format.getWriteParameters();
        writerParams.parameter(AbstractGridFormat.GEOTOOLS_WRITE_PARAMS.getName().toString()).setValue(wp);

        GridCoverageWriter writer = format.getWriter(output);
        writer.write(sourceCoverage, (GeneralParameterValue[]) writerParams.values().toArray(new GeneralParameterValue[1]));

        writer.dispose();
        output.close();
    }            
        
    private static void download(String filename) {
        String remotefile = "http://neso1.cryoland.enveo.at/cryoland/ows?service=wcs&request=GetCoverage&coverageid=" + filename;
        String localfile = LOCAL_REPOSITORY+filename;
        
        File dir = new File(LOCAL_REPOSITORY);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        File f = new File(localfile);
        if(f.exists() && !f.isDirectory()) { 
            LOG.info(localfile+" was already downloaded");
            return;
        }
        LOG.info("remotefile="+remotefile);
        LOG.info("localfile="+localfile);
        try {
            URLConnection http = new URL(remotefile).openConnection();
            InputStream in = http.getInputStream();
            try {
                OutputStream out = new FileOutputStream(localfile);
                try {
                    byte[] buf = new byte[512];
                    int read;
                    while ((read = in.read(buf)) > 0) {
                        out.write(buf, 0, read);
                    }
                } finally {
                    out.close();
                }
            } finally {
                in.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getLastTimeData(String collection, Integer ndays) throws Exception {
        try {
            ArrayList<String> thelist = SnowPack.getTimeData(collection, ndays);
            String filename = thelist.get(thelist.size() - 1);
            download(filename);
            return filename;
        } catch (Exception e) {
            return null;
        }
    }
    
    private static ArrayList<String> getTimeData(String collection) throws Exception {
        try {
            ArrayList<String> ret = SnowPack.getTimeData(collection, 15);
            for (String _filename : ret) {
                download(_filename);
            }
            return ret;
        } catch (Exception e) {
            return null;
        }
    }

    private static ArrayList<String> getTimeData(String collection, Integer ndays) throws Exception {
        ArrayList<String> ret = new ArrayList();
        
        DefaultHttpClient httpclient = new DefaultHttpClient();
        try {
            Calendar _calendar = Calendar.getInstance();
            String wpsinput = "";
            if (ndays != null) {
                _calendar.add(Calendar.DAY_OF_MONTH, -ndays);
                wpsinput =
                    "        <wps:Input>\n"+
                    "            <ows:Identifier>begin_time</ows:Identifier>\n"+
                    "            <wps:Data>\n"+
                    "                <wps:LiteralData>"+sdf.format(_calendar.getTime())+"T00:00:00Z</wps:LiteralData>\n"+
                    "            </wps:Data>\n"+
                    "        </wps:Input>\n";                
            }
        
            String body =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
                "<wps:Execute service=\"WPS\" version=\"1.0.0\" xmlns=\"http://www.opengis.net/wps/1.0.0\" xmlns:gml=\"http://www.opengis.net/gml\" xmlns:ogc=\"http://www.opengis.net/ogc\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" xmlns:wcs=\"http://www.opengis.net/wcs/1.1.1\" xmlns:wfs=\"http://www.opengis.net/wfs\" xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsAll.xsd\">\n"+
                "    <ows:Identifier>getTimeData</ows:Identifier>\n"+
                "    <wps:DataInputs>\n"+
                wpsinput +
                "        <wps:Input>\n"+
                "            <ows:Identifier>collection</ows:Identifier>\n"+
                "            <wps:Data>\n"+
                "                <wps:LiteralData>"+collection+"</wps:LiteralData>\n"+
                "            </wps:Data>\n"+
                "        </wps:Input>\n"+
                "    </wps:DataInputs>\n"+
                "    <wps:ResponseForm>\n"+
                "        <wps:RawDataOutput mimeType=\"text/csv\">\n"+
                "            <ows:Identifier>times</ows:Identifier>\n"+
                "        </wps:RawDataOutput>\n"+
                "    </wps:ResponseForm>\n"+
                "</wps:Execute>\n";
            
            StringEntity entity = new StringEntity(body,ContentType.TEXT_PLAIN);
            HttpPost httppost = new HttpPost("http://neso1.cryoland.enveo.at/cryoland/ows");
            httppost.setEntity(entity);
            
            LOG.info(httppost.getRequestLine().toString());
            LOG.info(body);

            // Create a custom response handler
            ResponseHandler<String> responseHandler = new ResponseHandler<String>() {

                @Override
                public String handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
                    int status = response.getStatusLine().getStatusCode();
                    if (status >= 200 && status < 300) {
                        HttpEntity entity = response.getEntity();
                        return entity != null ? EntityUtils.toString(entity) : null;
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + status + " " + response.getStatusLine().getReasonPhrase());
                    }
                }

            };
            String responseBody = httpclient.execute(httppost, responseHandler);
            LOG.info(responseBody);

            BufferedReader br = new BufferedReader(new StringReader(responseBody));
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                String[] columns = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
                String filename = columns[3].replaceAll("\"", "");
                LOG.info(filename);
                ret.add(filename);
            }
            
        } finally {
            //httpclient.close();
        }
        return ret;
    }
}
