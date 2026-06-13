package bg.transformit.ui;


import bg.transformit.core.Transform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;


/**
 * UI-layer wrapper around a {@link Transform} in the active pipeline.
 *
 * <p>Lives in the {@code ui} package (not {@code core}) because it carries a
 * JavaFX {@link BooleanProperty} for the enabled/disabled toggle — the core
 * layer is intentionally UI-free.
 */
public class PipelineEntry {

    private final Transform       transform;
    private final BooleanProperty enabled = new SimpleBooleanProperty(true);


    public PipelineEntry(Transform transform) {
        this.transform = transform;
    }

    public Transform getTransform() {
        return transform;
    }

    public BooleanProperty getEnabled() {
        return enabled;
    }

    public String displayName() {
        return transform.displayName();
    }
}
