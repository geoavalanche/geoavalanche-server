package org.geoavalanche.wps.ateinorm;

import com.github.dexecutor.core.task.Task;
import es.unex.sextante.core.AnalysisExtent;
import es.unex.sextante.dataObjects.IRasterLayer;

public class TaskAspect extends Task<String, Boolean> {

    public static final String NAME = "TaskAspect";
    private IRasterLayer aspect;
    private IRasterLayer dem;
    private int method;
    private int unit;
    private AnalysisExtent ext;

    public TaskAspect() {
        setId(NAME);
    }

    public TaskAspect(IRasterLayer dem, int method, int unit, AnalysisExtent ext) {
        this.dem = dem;
        this.method = method;
        this.unit = unit;
        this.ext = ext;
    }

    public IRasterLayer getAspect() {
        return aspect;
    }
    
    @Override
    public Boolean execute() {
        try {
            aspect = ATEINorm.getAspect(dem, method, unit, ext);
            return true;
        } catch(Exception e) {
            return false;
        }
    }
}
