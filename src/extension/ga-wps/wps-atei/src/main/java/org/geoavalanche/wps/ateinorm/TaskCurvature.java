package org.geoavalanche.wps.ateinorm;

import com.github.dexecutor.core.task.Task;
import es.unex.sextante.core.AnalysisExtent;
import es.unex.sextante.dataObjects.IRasterLayer;

public class TaskCurvature extends Task<String, Boolean> {

    public static final String NAME = "TaskCurvature";
    private IRasterLayer curvature;
    private IRasterLayer dem;
    private String method;
    private AnalysisExtent ext;

    public TaskCurvature() {
        setId(NAME);
    }

    public TaskCurvature(IRasterLayer dem, String method, AnalysisExtent ext) {
        this.dem = dem;
        this.method = method;
        this.ext = ext;
    }

    public IRasterLayer getCurvature() {
        return curvature;
    }

    @Override
    public Boolean execute() {
        try {
            curvature = ATEINorm.getCurvature(dem, method, ext);
            return true;
        } catch(Exception e) {
            return false;
        }
    }
}
