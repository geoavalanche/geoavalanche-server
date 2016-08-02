package org.geoavalanche.wps.snowpack;

import com.vividsolutions.jts.geom.Geometry;
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
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.factory.StaticMethodsProcessFactory;
import org.geotools.text.Text;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class SnowPack extends StaticMethodsProcessFactory<SnowPack> {

    private static final Logger LOG = Logger.getLogger(SnowPack.class.getName());
    private static final CoverageProcessor PROCESSOR = CoverageProcessor.getInstance();
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    private static final String LOCAL_REPOSITORY = "c:/tmp/";


    public SnowPack() {
        super(Text.text("GeoAvalanche"), "geoavalanche", SnowPack.class);
    }

    @DescribeProcess(title = "SnowPack", description = "Calculate snowmelt and snowcover for all geometries in the collection")
    @DescribeResult(description = "FeatureCollection")
    public static SimpleFeatureCollection SnowPack(
            @DescribeParameter(name = "FeatureCollection", description = "FeatureCollection (with crs EPSG:4326)", min = 1, max = 1) SimpleFeatureCollection featureCollection
    ) throws Exception {

        List<SimpleFeature> featuresList = new ArrayList<SimpleFeature>();
        SimpleFeatureBuilder fb = getSimpleFeatureBuilder(featureCollection);
        
        ArrayList<String> filenames = getTimeData("daily_SWE_PanEuropean_Microwave");
        for (String _filename : filenames) {
            download(_filename);
        }  
            
        SimpleFeatureIterator itr = featureCollection.features();
        while (itr.hasNext()) {
            SimpleFeature feature = itr.next();
            fb.reset();
            for (Property p : feature.getProperties()) {
                fb.set(p.getName().getLocalPart(), p.getValue());
            }
            Geometry theGeometry = (Geometry)feature.getAttribute("the_geom");
            if (theGeometry == null) {
                theGeometry = (Geometry)feature.getAttribute("geometry");
            }
            if (theGeometry != null) {
                fb.set("snowmelt", snowmelt(theGeometry, filenames));
                fb.set("snowcover", "NA");
            }

            featuresList.add(fb.buildFeature(feature.getID()));
        }

        SimpleFeatureCollection ret = new ListFeatureCollection(fb.getFeatureType(), featuresList);
        LOG.info("nrec = " + ret.size());
        return ret;
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
        theProperties.put("snowmelt", String.class);
        theProperties.put("snowcover", String.class);

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

    static String snowmelt(Geometry theGeometry, ArrayList<String> filenames) throws Exception {
        String ret = null;
        for (String _filename : filenames) {
            if (ret == null) {
                ret = snowmelt(theGeometry, _filename);
            } else {
                ret = ret+","+snowmelt(theGeometry, _filename);
            }
        }
        return ret;
    }
    
    static String snowmelt(Geometry theGeometry, String filename) throws Exception {

        File file = new File(LOCAL_REPOSITORY+filename);
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

        ParameterValueGroup paramsExtrema = PROCESSOR.getOperation("Extrema").getParameters();
        paramsExtrema.parameter("Source").setValue(cropped);
        GridCoverage2D result = (GridCoverage2D) PROCESSOR.doOperation(paramsExtrema, null);
        double[] minimum = (double[]) result.getProperty("minimum");
        double[] maximum = (double[]) result.getProperty("maximum");
        for (int x = 0; x < minimum.length; x++) {
            LOG.info("minimum[" + x + "]=" + minimum[x]);
        }
        for (int x = 0; x < maximum.length; x++) {
            LOG.info("maximum[" + x + "]=" + maximum[x]);
        }
        //SWE = (CODE – 100) * 4.0 
        double swe = (maximum[0]>=100&maximum[0]<=200)?((maximum[0]-100)*4):(-maximum[0]);
        LOG.info("snow water equivalent (SWE) = " + swe + " mm");

        return "" + swe;
    }

    private static void download(String filename) {
        String remotefile = "http://neso1.cryoland.enveo.at/cryoland/ows?service=wcs&request=GetCoverage&coverageid=" + filename;
        String localfile = LOCAL_REPOSITORY+filename;
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

    private static ArrayList<String> getTimeData(String collection) throws Exception {
        ArrayList<String> ret = new ArrayList();
        
        DefaultHttpClient httpclient = new DefaultHttpClient();
        try {
            Calendar _calendar = Calendar.getInstance();
            _calendar.add(Calendar.DAY_OF_MONTH, -7);
            sdf.format(_calendar.getTime());
        
            String body =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
                "<wps:Execute service=\"WPS\" version=\"1.0.0\" xmlns=\"http://www.opengis.net/wps/1.0.0\" xmlns:gml=\"http://www.opengis.net/gml\" xmlns:ogc=\"http://www.opengis.net/ogc\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" xmlns:wcs=\"http://www.opengis.net/wcs/1.1.1\" xmlns:wfs=\"http://www.opengis.net/wfs\" xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsAll.xsd\">\n"+
                "    <ows:Identifier>getTimeData</ows:Identifier>\n"+
                "    <wps:DataInputs>\n"+
                "        <wps:Input>\n"+
                "            <ows:Identifier>begin_time</ows:Identifier>\n"+
                "            <wps:Data>\n"+
                "                <wps:LiteralData>"+sdf.format(_calendar.getTime())+"T00:00:00Z</wps:LiteralData>\n"+
                "            </wps:Data>\n"+
                "        </wps:Input>\n"+
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
    
    public static void main(String[] args) {
        try {         
            ArrayList<String> filenames = getTimeData("daily_SWE_PanEuropean_Microwave");
            for (String _filename : filenames) {
                download(_filename);
            }            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
