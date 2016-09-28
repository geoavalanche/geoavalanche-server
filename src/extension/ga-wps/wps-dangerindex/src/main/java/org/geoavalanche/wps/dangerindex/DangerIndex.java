package org.geoavalanche.wps.dangerindex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import java.util.HashMap;
import java.util.StringTokenizer;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.factory.StaticMethodsProcessFactory;
import org.geotools.text.Text;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class DangerIndex extends StaticMethodsProcessFactory<DangerIndex> {

    private static final Logger LOG = Logger.getLogger(DangerIndex.class.getName());

    public DangerIndex() {
        super(Text.text("GeoAvalanche"), "geoavalanche", DangerIndex.class);
    }

    @DescribeProcess(title = "DangerIndex", description = "DangerIndex")
    @DescribeResult(description = "FeatureCollection")
    public static SimpleFeatureCollection DangerIndex(
            @DescribeParameter(name = "FeatureCollection", description = "FeatureCollection") SimpleFeatureCollection featureCollection
            ) throws Exception {
        try {        
            List<SimpleFeature> featuresList = new ArrayList<SimpleFeature>();
            SimpleFeatureBuilder fb = getSimpleFeatureBuilder(featureCollection);

            SimpleFeatureIterator itr = featureCollection.features();
            while (itr.hasNext()) {
                long incidentsInLastWeek = 0;
                double fsc = 0;
                double atei = 0;
                long dangerindex = 0;
                SimpleFeature feature = itr.next();
                fb.reset();
                for (Property p : feature.getProperties()) {
                    fb.set(p.getName().getLocalPart(), p.getValue());
                    if (p.getName().getLocalPart().equalsIgnoreCase("incidents")) {
                        incidentsInLastWeek = getIncidentsInLastWeek((String)p.getValue());
                    }
                    if (p.getName().getLocalPart().equalsIgnoreCase("fsc")) {
                        fsc = getFsc((String)p.getValue());
                    }                
                    if (p.getName().getLocalPart().equalsIgnoreCase("atei")) {
                        atei = getAtei((String)p.getValue());
                    }                
                }

                dangerindex = getDangerIndex(incidentsInLastWeek, fsc, atei);            
                fb.set("dangerindex", ""+dangerindex);
                featuresList.add(fb.buildFeature(feature.getID()));
            }

            SimpleFeatureCollection ret = new ListFeatureCollection(fb.getFeatureType(), featuresList);
            LOG.info("nrec = " + ret.size());
            return ret;
        } catch(Exception e) {
            e.printStackTrace();
            return featureCollection;
        }
    }
    
    static long getDangerIndex(long incidentsInLastWeek, double fsc, double atei) {
        if (incidentsInLastWeek >= 1) {
            return (long)Math.ceil(fsc / 100 * atei);
        } else {
            return (long)Math.floor(fsc / 100 * atei);
        }
    }
    
    static long getIncidentsInLastWeek(String theValue) {
        StringTokenizer st = new StringTokenizer(theValue, ",");
        long _incidents = Long.parseLong(st.nextToken());
        long _incidentsInLastYear = Long.parseLong(st.nextToken());
        long _incidentsInLastMonth = Long.parseLong(st.nextToken());
        long _incidentsInLastWeek = Long.parseLong(st.nextToken());
        return _incidentsInLastWeek; 
    }

    static double getFsc(String theValue) {
        double ret=0;
        StringTokenizer st = new StringTokenizer(theValue, ",");
        try {
            double fsc = Double.parseDouble(st.nextToken()); //daily_FSC_PanEuropean_Optical
            if (fsc >= 100 && fsc <= 200) {
                if ((fsc - 100) > ret) {
                    ret = fsc - 100;
                }
            }
        } catch (Exception e) {
        }
        try {
            double fsc = Double.parseDouble(st.nextToken()); //daily_FSC_Alps_Optical
            if (fsc >= 100 && fsc <= 200) {
                if ((fsc - 100) > ret) {
                    ret = fsc - 100;
                }
            }
        } catch (Exception e) {
        }
        try {
            double fsc = Double.parseDouble(st.nextToken()); //daily_FSC_Baltic_Optical
            if (fsc >= 100 && fsc <= 200) {
                if ((fsc - 100) > ret) {
                    ret = fsc - 100;
                }
            }
        } catch (Exception e) {
        }

        return ret; 
    }
    
    static double getAtei(String theValue) {
        double ret = Double.parseDouble(theValue);
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
        theProperties.put("dangerindex", String.class);

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

    private static String fakeIndex() {
        String ret = "" + (System.currentTimeMillis() % 3);
        return ret;
    }
}
