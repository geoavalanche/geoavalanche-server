package org.geoavalanche.alg.avalanche;

import es.unex.sextante.core.AnalysisExtent;
import es.unex.sextante.core.GeoAlgorithm;
import es.unex.sextante.core.Sextante;
import es.unex.sextante.dataObjects.IRasterLayer;
import es.unex.sextante.exceptions.GeoAlgorithmExecutionException;
import es.unex.sextante.exceptions.RepeatedParameterNameException;

/**
 * Avalanche Terrain Exposure based on a reclassification of slope, aspect, curvature
 * and land use
 *
 */
public class AvalancheTerrainExposureAlgorithm extends GeoAlgorithm
{
    public static final String  SLOPE       = "SLOPE";
    public static final String  ASPECT      = "ASPECT";
    public static final String  CURVATURE   = "CURVATURE";
    public static final String  LANDCLASS   = "LANDCLASS";
    public static final String  ATEI        = "ATEI";
    
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
        
        m_Slope = m_Parameters.getParameterValueAsRasterLayer(SLOPE);
        m_Aspect = m_Parameters.getParameterValueAsRasterLayer(ASPECT);
        m_Curvature = m_Parameters.getParameterValueAsRasterLayer(CURVATURE);
        m_LandClassification = m_Parameters.getParameterValueAsRasterLayer(LANDCLASS);
        
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
        
        double dSlope     = m_Slope.getCellValueAsDouble(x, y);
        double dAspect    = m_Aspect.getCellValueAsDouble(x, y);
        double dCurvature = m_Curvature.getCellValueAsDouble(x, y);
        double dLandClass = m_LandClassification.getCellValueAsDouble(x, y);
        
        //TODO fill nodata with reasonable value
        if (m_Slope.isNoDataValue(dSlope) || m_Aspect.isNoDataValue(dAspect) || 
                m_Curvature.isNoDataValue(dCurvature) || m_LandClassification.isNoDataValue(dLandClass)) {
            m_AvalancheTerrainExposureIndex.setNoData(x, y);
         }
         else {
            
            m_AvalancheTerrainExposureIndex.setCellValue(y, y, dSlope);
            //dAccFlow /= m_AccFlow.getWindowCellSize();
            //dSlope = Math.max(Math.tan(dSlope), ALMOST_ZERO);
            //m_WetnessIndex.setCellValue(x, y, Math.log(dAccFlow / dSlope));
            //m_StreamPowerIndex.setCellValue(x, y, dAccFlow * dSlope);
            //m_LSFactor.setCellValue(x, y, (0.4 + 1) * Math.pow(dAccFlow / 22.13, 0.4) * Math.pow(Math.sin(dSlope) / 0.0896, 1.3));
         }
        
    }
        
}
