package qupath.ext.spclassify;

import qupath.ext.spclassify.io.ProjectStateManager;
import qupath.ext.spclassify.model.BatchShifts;
import qupath.ext.spclassify.model.CellFeatureExtractor;
import qupath.lib.projects.Project;

/**
 * Small facade for streaming UniFORM batch correction into feature extraction. When the
 * {@link SpClassifyExtension#useBatchCorrection() toggle} is on and a project has persisted
 * scales ({@code batch-shifts.json}), callers load the {@link BatchShifts} once and apply a
 * per-image gain to each {@link CellFeatureExtractor} they build inside their per-image loops
 * — the extractor multiplies each measurement by its image's per-channel scale before any
 * transform. No corrected columns are written; correction is entirely in-memory and re-fittable.
 */
public final class BatchCorrection {

    private BatchCorrection() {}

    /** The project's persisted scales if the toggle is on and a fit exists, else {@code null}. */
    public static BatchShifts loadIfEnabled(Project<?> project) {
        if (project == null || !SpClassifyExtension.useBatchCorrection()) {
            return null;
        }
        BatchShifts shifts = ProjectStateManager.loadBatchShifts(project);
        return (shifts == null || shifts.isEmpty()) ? null : shifts;
    }

    /** Fingerprint of {@code shifts}, or {@code null} when correction is not in use. */
    public static String fingerprint(BatchShifts shifts) {
        return shifts == null ? null : shifts.fingerprint();
    }

    /**
     * Why predicting with the current correction differs from training, or {@code null} if it
     * does not (or the model predates this record, {@code trainedUsed == null}).
     *
     * @param trainedUsed        whether correction was applied at training
     * @param trainedFingerprint {@link #fingerprint} of the gains used at training
     * @param current            the gains that prediction will use ({@link #loadIfEnabled})
     */
    public static String mismatch(Boolean trainedUsed, String trainedFingerprint, BatchShifts current) {
        if (trainedUsed == null) return null;
        boolean usedNow = current != null;
        if (trainedUsed != usedNow) {
            return "Batch correction was " + (trainedUsed ? "on" : "off") + " when this classifier was trained and is "
                    + (usedNow ? "on" : "off") + " now. Predictions use the current setting; retrain to match.";
        }
        if (usedNow && trainedFingerprint != null && !trainedFingerprint.equals(current.fingerprint())) {
            return "The batch-correction gains were re-fitted after this classifier was trained. Predictions use the"
                    + " new gains; retrain to match.";
        }
        return null;
    }

    /**
     * Set the per-image gain on {@code extractor} from {@code shifts} for {@code imageName}.
     * No-op when {@code shifts} is {@code null} (toggle off / no fit) or arguments are missing,
     * so callers can wire it unconditionally.
     */
    public static void applyTo(CellFeatureExtractor extractor, BatchShifts shifts, String imageName) {
        if (extractor == null || shifts == null || imageName == null) {
            return;
        }
        extractor.setBatchScale(shifts.scaleArray(imageName, extractor.getFeatureNames()));
    }
}
