package org.geoavalanche.alg.avalanche;

import es.unex.sextante.core.AnalysisExtent;
import es.unex.sextante.core.GeoAlgorithm;
import es.unex.sextante.core.ParametersSet;
import es.unex.sextante.core.Sextante;
import es.unex.sextante.dataObjects.IRasterLayer;
import es.unex.sextante.exceptions.GeoAlgorithmExecutionException;
import es.unex.sextante.exceptions.RepeatedParameterNameException;
import es.unex.sextante.gridCategorical.reclassify.ReclassifyAlgorithm;
import es.unex.sextante.parameters.FixedTableModel;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;

/**
 * Avalanche Terrain Exposure based on a reclassification of slope, aspect, curvature
 * and land use
 *
 */
public class AvalancheTerrainExposureAlgorithm extends GeoAlgorithm
{
    private static final double ALMOST_ZERO  = 0.0011;
    private static final double SLOPE_COEFF  = 0.4000;
    private static final double ASPECT_COEFF = 0.1500;
    private static final double CURVAT_COEFF = 0.2000;
    private static final double LCLASS_COEFF = 0.2500;
    
    public static final String  SLOPE       = "SLOPE";
    public static final String  ASPECT      = "ASPECT";
    public static final String  CURVATURE   = "CURVATURE";
    public static final String  LANDCLASS   = "LANDCLASS";
    public static final String  ATEI        = "ATEI";
    
    private static final Logger LOG = Logging.getLogger(AvalancheTerrainExposureAlgorithm.class.getName());
    
    private IRasterLayer        m_Slope                 = null;
    private IRasterLayer        m_Aspect                = null;
    private IRasterLayer        m_Curvature             = null;
    private IRasterLayer        m_LandClassification    = null;
    private IRasterLayer        m_AvalancheTerrainExposureIndex;

    @Override
    public void defineCharacteristics() {
        
        setName(Sextante.getText("Avalanche_Terrain_indices"));
        setGroup(Sextante.getText("Indices_and_other_avalanche_parameters"));
        setUserCanDefineAnalysisExtent(true);
        
        try {
            m_Parameters.addInputRasterLayer(SLOPE, Sextante.getText("Slope"), true);
            m_Parameters.addInputRasterLayer(ASPECT, Sextante.getText("Aspect"), true);
            m_Parameters.addInputRasterLayer(CURVATURE, Sextante.getText("Curvature"), true);
            m_Parameters.addInputRasterLayer(LANDCLASS, Sextante.getText("Land_Classification"), true);
            addOutputRasterLayer(ATEI, Sextante.getText("Avalanche_Terrain_Exposure_Index__ATEI"));
        } catch (final RepeatedParameterNameException e) {
            Sextante.addErrorToLog(e);
        }
        
    }

    @Override
    public boolean processAlgorithm() throws GeoAlgorithmExecutionException {
        
        int x, y;
        int iNX, iNY;
        
        //Applied initial reclassifications for ATEI algorithm
        IRasterLayer c_Slope = getATEIReclassification(m_Parameters.getParameterValueAsRasterLayer(SLOPE), m_Parameters.getParameterValueAsRasterLayer(SLOPE).getLayerGridExtent(), SLOPE);
        IRasterLayer c_Aspect = getATEIReclassification(m_Parameters.getParameterValueAsRasterLayer(ASPECT), m_Parameters.getParameterValueAsRasterLayer(ASPECT).getLayerGridExtent(), ASPECT);
        IRasterLayer c_Curvature = getATEIReclassification(m_Parameters.getParameterValueAsRasterLayer(CURVATURE), m_Parameters.getParameterValueAsRasterLayer(CURVATURE).getLayerGridExtent(), CURVATURE);
        IRasterLayer c_LandClassification = getATEIReclassification(m_Parameters.getParameterValueAsRasterLayer(LANDCLASS), m_Parameters.getParameterValueAsRasterLayer(LANDCLASS).getLayerGridExtent(), LANDCLASS);
        
        m_Slope = c_Slope;
        m_Aspect = c_Aspect;
        m_Curvature = c_Curvature;
        m_LandClassification = c_LandClassification;
        
        m_AvalancheTerrainExposureIndex = getNewRasterLayer(ATEI, Sextante.getText("Avalanche_Terrain_Exposure_Index__ATEI"),
               IRasterLayer.RASTER_DATA_TYPE_FLOAT);
        
        final AnalysisExtent extent = m_AvalancheTerrainExposureIndex.getWindowGridExtent();
        m_Slope.setWindowExtent(extent);
        m_Aspect.setWindowExtent(extent);
        m_Curvature.setWindowExtent(extent);
        m_LandClassification.setWindowExtent(extent);
        
        iNX = m_Slope.getNX();
        iNY = m_Slope.getNY();
        
        for (y = 0; (y < iNY) && setProgress(y, iNY); y++) {
         for (x = 0; x < iNX; x++) {
            calculateIndices(x, y);
         }
      }

      return !m_Task.isCanceled();
   }

    private void calculateIndices(int x, int y) {
        
        /*
         * 0 - NO RISK
         * 1 - LOW RISK
         * 2 - MODERATE RISK
         * 3 - HIGH RISK
        */
        
        double dSlope     = m_Slope.getCellValueAsDouble(x, y);
        double dAspect    = m_Aspect.getCellValueAsDouble(x, y);
        double dCurvature = m_Curvature.getCellValueAsDouble(x, y);
        // Assuming base value 0 for land classification to do not break algorithm
        double dLandClass = 0;
        
        if (m_LandClassification == null) {
            LOG.severe("Land Use classification layer is null");
        } else
            dLandClass = m_LandClassification.getCellValueAsDouble(x, y);
        double dATEI      = 0;
        
        // TODO fill nodata with reasonable value
        if (m_Slope.isNoDataValue(dSlope) || m_Aspect.isNoDataValue(dAspect) || 
                m_Curvature.isNoDataValue(dCurvature) || m_LandClassification.isNoDataValue(dLandClass)) {
            m_AvalancheTerrainExposureIndex.setNoData(x, y);
         }
         else {
            
            dATEI = ((dSlope * SLOPE_COEFF) + (dAspect * ASPECT_COEFF) + (dCurvature * CURVAT_COEFF) + (dLandClass * LCLASS_COEFF));
            LOG.info("dSlope * SLOPE_COEFF="+(dSlope * SLOPE_COEFF)+"dAspect * ASPECT_COEFF="+(dAspect * ASPECT_COEFF)+"dCurvature * CURVAT_COEFF="+(dCurvature * CURVAT_COEFF)+
                    "dLandClass * LCLASS_COEFF="+(dLandClass * LCLASS_COEFF));
            m_AvalancheTerrainExposureIndex.setCellValue(x, y, dATEI);
            LOG.info("The value of cell x,y="+x+","+y+" is "+dATEI);
         }
        
    }
    
    private IRasterLayer getATEIReclassification(IRasterLayer input, AnalysisExtent gridExt, String type) throws GeoAlgorithmExecutionException {
        
        FixedTableModel lut = null;
        final String    SLOPE                       = "SLOPE";
        final String    ASPECT                      = "ASPECT";
        final String    CURVATURE                   = "CURVATURE";
        final String    LANDCLASS                   = "LANDCLASS";
        final int       METHOD_LOWER_THAN           = ReclassifyAlgorithm.METHOD_LOWER_THAN;
        final int       METHOD_LOWER_THAN_OR_EQUAL  = ReclassifyAlgorithm.METHOD_LOWER_THAN_OR_EQUAL;
        
        
        String[] cols = {"Min_value","Max_value","New_value"};
        
        //SLOPE CLASSES {0,25,1},{25,45,3},{45,60,1},{60,90,0}
        FixedTableModel slopeTable = new FixedTableModel(cols, 6, true);
        //first row
        slopeTable.setValueAt(0.0, 1, 1);
        slopeTable.setValueAt(25.0, 1, 2);
        slopeTable.setValueAt(1.0, 1, 3);
        //second row
        slopeTable.setValueAt(25.0, 2, 1);
        slopeTable.setValueAt(45.0, 2, 2);
        slopeTable.setValueAt(3.0, 2, 3);
        //third row
        slopeTable.setValueAt(45.0, 3, 1);
        slopeTable.setValueAt(60.0, 3, 2);
        slopeTable.setValueAt(1.0, 3, 3);
        //fourth row
        slopeTable.setValueAt(60.0, 4, 1);
        slopeTable.setValueAt(90.0, 4, 2);
        slopeTable.setValueAt(0.0, 4, 3);
        //nodata
        slopeTable.setValueAt(-9999.0, 5, 1);
        slopeTable.setValueAt(0.0, 5, 2);
        slopeTable.setValueAt(0.0, 5, 3);
        //more nodata
        slopeTable.setValueAt(90.0, 6, 1);
        slopeTable.setValueAt(9999.0, 6, 2);
        slopeTable.setValueAt(0.0, 6, 3);
        LOG.info("slopeTable is "+slopeTable.toString());
        
        //ASPECT CLASSES {0.0,45,3},{45.0,135.0,2},{135.0,315.0,1},{315.0,360.0,3}
        FixedTableModel aspectTable = new FixedTableModel(cols, 6, true);
        //NORTH
        aspectTable.setValueAt(0.0, 1, 1);
        aspectTable.setValueAt(45.0, 1, 2);
        aspectTable.setValueAt(3.0, 1, 3);
        //EAST
        aspectTable.setValueAt(45.0, 2, 1);
        aspectTable.setValueAt(135.0, 2, 2);
        aspectTable.setValueAt(2.0, 2, 3);
        //SOUTH - WEST
        aspectTable.setValueAt(135.0, 3, 1);
        aspectTable.setValueAt(315.0, 3, 2);
        aspectTable.setValueAt(1.0, 3, 3);
        //NORTH
        aspectTable.setValueAt(315.0, 4, 1);
        aspectTable.setValueAt(360.0, 4, 2);
        aspectTable.setValueAt(3.0, 4, 3);
        //nodata
        aspectTable.setValueAt(-9999.0, 5, 1);
        aspectTable.setValueAt(0.0, 5, 2);
        aspectTable.setValueAt(0.0, 5, 3);
        //more nodata
        aspectTable.setValueAt(360.0, 6, 1);
        aspectTable.setValueAt(9999.0, 6, 2);
        aspectTable.setValueAt(0.0, 6, 3);
        LOG.info("aspectTable is "+aspectTable.toString());
        
        //CURVATURE CLASSES {0.0,1.0,3},{-1.0,0.0,1}
        FixedTableModel curvTable = new FixedTableModel(cols, 4, true);
        //CONCAVE
        curvTable.setValueAt(0.0, 1, 1);
        curvTable.setValueAt(1.0, 1, 2);
        curvTable.setValueAt(3.0, 1, 3);
        //CONVEX
        curvTable.setValueAt(-1.0, 2, 1);
        curvTable.setValueAt(0.0, 2, 2);
        curvTable.setValueAt(1.0, 2, 3);
        //nodata
        curvTable.setValueAt(-9999.0, 3, 1);
        curvTable.setValueAt(-1.0, 3, 2);
        curvTable.setValueAt(0.0, 3, 3);
        //more nodata
        curvTable.setValueAt(1.0, 4, 1);
        curvTable.setValueAt(9999.0, 4, 2);
        curvTable.setValueAt(0.0, 4, 3);
        
        LOG.info("curvTable is "+curvTable.toString());
        
        //LAND CLASSES FROM COPERNICUS {,,},{,,}, see clc_legend_ATEI
        FixedTableModel lclassTable = new FixedTableModel(cols, 18, true);
        //Continuous urban fabric/Discontinuous urban fabric/Industrial or commercial units
        lclassTable.setValueAt(0.0, 1, 1);
        lclassTable.setValueAt(3.0, 1, 2);
        lclassTable.setValueAt(0.0, 1, 3);
        //Road and rail networks and associated land
        lclassTable.setValueAt(3.0, 2, 1);
        lclassTable.setValueAt(4.0, 2, 2);
        lclassTable.setValueAt(1.0, 2, 3);
        //Port areas/Airports/Mineral extraction sites/Dump sites/Construction sites/Green urban areas
        //Sport and leisure facilities/Non-irrigated arable land/Permanently irrigated land/Rice fields/
        //Vineyards/Fruit trees and berry plantations/Olive groves/
        lclassTable.setValueAt(4.0, 3, 1);
        lclassTable.setValueAt(17.0, 3, 2);
        lclassTable.setValueAt(0.0, 3, 3);
        //Pastures
        lclassTable.setValueAt(17.0, 4, 1);
        lclassTable.setValueAt(18.0, 4, 2);
        lclassTable.setValueAt(2.0, 4, 3);
        //Annual crops associated with permanent crops/Complex cultivation patterns/
        lclassTable.setValueAt(18.0, 5, 1);
        lclassTable.setValueAt(20.0, 5, 2);
        lclassTable.setValueAt(0.0, 5, 3);
        //Land principally occupied by agriculture with significant areas of natural vegetation/
        //Agro-forestry areas/Broad-leaved forest/Coniferous forest/Mixed forest
        lclassTable.setValueAt(20.0, 6, 1);
        lclassTable.setValueAt(25.0, 6, 2);
        lclassTable.setValueAt(1.0, 6, 3);
        //Natural grasslands
        lclassTable.setValueAt(25.0, 7, 1);
        lclassTable.setValueAt(26.0, 7, 2);
        lclassTable.setValueAt(3.0, 7, 3);
        //Moors and heathland
        lclassTable.setValueAt(26.0, 8, 1);
        lclassTable.setValueAt(27.0, 8, 2);
        lclassTable.setValueAt(2.0, 8, 3);
        //Sclerophyllous vegetation
        lclassTable.setValueAt(27.0, 9, 1);
        lclassTable.setValueAt(28.0, 9, 2);
        lclassTable.setValueAt(3.0, 9, 3);
        //Transitional woodland-shrub
        lclassTable.setValueAt(28.0, 10, 1);
        lclassTable.setValueAt(29.0, 10, 2);
        lclassTable.setValueAt(2.0, 10, 3);
        //Beaches - dunes - sands
        lclassTable.setValueAt(29.0, 10, 1);
        lclassTable.setValueAt(30.0, 10, 2);
        lclassTable.setValueAt(0.0, 10, 3);
        //Bare rocks
        lclassTable.setValueAt(30.0, 11, 1);
        lclassTable.setValueAt(31.0, 11, 2);
        lclassTable.setValueAt(2.0, 11, 3);
        //Sparsely vegetated areas
        lclassTable.setValueAt(31.0, 12, 1);
        lclassTable.setValueAt(32.0, 12, 2);
        lclassTable.setValueAt(3.0, 12, 3);
        //Burnt areas
        lclassTable.setValueAt(32.0, 13, 1);
        lclassTable.setValueAt(33.0, 13, 2);
        lclassTable.setValueAt(0.0, 13, 3);
        //Glaciers and perpetual snow
        lclassTable.setValueAt(33.0, 14, 1);
        lclassTable.setValueAt(34.0, 14, 2);
        lclassTable.setValueAt(3.0, 14, 3);
        //Inland marshes/Peat bogs/
        lclassTable.setValueAt(34.0, 15, 1);
        lclassTable.setValueAt(36.0, 15, 2);
        lclassTable.setValueAt(1.0, 15, 3);
        //Salt marshes/Salines/Intertidal flats/Water courses/Water bodies/Coastal lagoons
        //Estuaries/Sea and ocean/NODATA/UNCLASSIFIED LAND SURFACE/UNCLASSIFIED WATER BODIES
        //UNCLASSIFIED
        lclassTable.setValueAt(36.0, 16, 1);
        lclassTable.setValueAt(255.0, 16, 2);
        lclassTable.setValueAt(0.0, 16, 3);
        //nodata
        curvTable.setValueAt(-9999.0, 17, 1);
        curvTable.setValueAt(0.0, 17, 2);
        curvTable.setValueAt(0.0, 17, 3);
        //more nodata
        curvTable.setValueAt(255.0, 18, 1);
        curvTable.setValueAt(9999.0, 18, 2);
        curvTable.setValueAt(0.0, 18, 3);
        LOG.info("lclassTable is "+lclassTable.toString());
        
        switch (type) {
            case SLOPE:
                lut = slopeTable;
                LOG.info("The table model selected is related to "+type);
                break;
            case ASPECT:
                lut = aspectTable;
                LOG.info("The table model selected is related to "+type);
                break;
            case CURVATURE:
                lut = curvTable;
                LOG.info("The table model selected is related to "+type);
                break;
            case LANDCLASS:
                lut = lclassTable;
                LOG.info("The table model selected is related to "+type);
                break;
            default:
                LOG.info("The table model selected is related to "+type);
                break;
        }
        
        input.setWindowExtent(gridExt);
        
        ReclassifyAlgorithm reclassAlg = new ReclassifyAlgorithm();
        reclassAlg.setAnalysisExtent(gridExt);
        ParametersSet params = reclassAlg.getParameters();
        params.getParameter(ReclassifyAlgorithm.INPUT).setParameterValue(input);
        params.getParameter(ReclassifyAlgorithm.LUT).setParameterValue(lut);
        params.getParameter(ReclassifyAlgorithm.METHOD).setParameterValue(METHOD_LOWER_THAN_OR_EQUAL);
        
        reclassAlg.execute(m_Task, m_OutputFactory);
        final IRasterLayer result = (IRasterLayer) reclassAlg.getOutputObjects().getOutput(ReclassifyAlgorithm.RECLASS).getOutputObject();
        result.open();
        
        if (m_Task.isCanceled()) {
            
            LOG.info("The execution of the following algorithm has been cancelled ---> "+reclassAlg.getName());
            return null;
            
        }
        
        return result;
        
    }
    
        
}
