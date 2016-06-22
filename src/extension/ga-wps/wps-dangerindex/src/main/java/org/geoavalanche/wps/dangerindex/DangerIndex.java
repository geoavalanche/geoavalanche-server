package org.geoavalanche.wps.dangerindex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import java.util.HashMap;
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
        
        List<SimpleFeature> featuresList = new ArrayList<SimpleFeature>();
        SimpleFeatureBuilder fb = getSimpleFeatureBuilder(featureCollection);

        SimpleFeatureIterator itr = featureCollection.features();
        while (itr.hasNext()) {
            long[] incidents = {0,0,0,0};
            SimpleFeature feature = itr.next();
            fb.reset();
            for (Property p : feature.getProperties()) {
                fb.set(p.getName().getLocalPart(), p.getValue());
                if (p.getName().getLocalPart().equalsIgnoreCase("incidents")) {
                    incidents = (long[])p.getValue();
                }
            }
            if (incidents[0] == 1) {
                fb.set("dangerindex", "1");
            } else if (incidents[0] > 1) {
                fb.set("dangerindex", "2");
            } else {
                fb.set("dangerindex", "0");
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
