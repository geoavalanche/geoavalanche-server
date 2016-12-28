package org.geoavalanche.wps.ateinorm;

import com.github.dexecutor.core.task.Task;
import es.unex.sextante.core.AnalysisExtent;
import es.unex.sextante.dataObjects.IRasterLayer;

public class TaskSlope extends Task<String, Boolean> {

    public static final String NAME = "TaskSlope";
    private IRasterLayer slope;
    private IRasterLayer dem;
    private int method;
    private int unit;
    private AnalysisExtent ext;

    public TaskSlope() {
        setId(NAME);
    }

    public TaskSlope(IRasterLayer dem, int method, int unit, AnalysisExtent ext) {
        this.dem = dem;
        this.method = method;
        this.unit = unit;
        this.ext = ext;
    }

    public IRasterLayer getSlope() {
        return slope;
    }
    
    @Override
    public Boolean execute() {
        try {
            slope = ATEINorm.getSlope(dem, method, unit, ext);
            return true;
        } catch(Exception e) {
            return false;
        }
    }
}
