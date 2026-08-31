package com.againspring.service.community;

/**
 * One sibom placement on a channel script timeline ({@code sibom_plan[]} item).
 *
 * @see docs/shared/marketing/70-policy/sibom-video-insertion.md §5.1
 */
public record SibomPlanItem(
        String role,
        String imageId,
        String caption,
        Integer beatIndex,
        String size,
        String dwell
) {
    public SibomPlanItem withRole(String newRole) {
        return new SibomPlanItem(newRole, imageId, caption, beatIndex, size, dwell);
    }

    public SibomPlanItem withImageId(String newImageId) {
        return new SibomPlanItem(role, newImageId, caption, beatIndex, size, dwell);
    }

    public SibomPlanItem withCaption(String newCaption) {
        return new SibomPlanItem(role, imageId, newCaption, beatIndex, size, dwell);
    }

    public SibomPlanItem withSizeDwell(String newSize, String newDwell) {
        return new SibomPlanItem(role, imageId, caption, beatIndex, newSize, newDwell);
    }
}
