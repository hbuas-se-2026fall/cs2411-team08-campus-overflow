package com.campusoverflow.reputation.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "rep_course_budget")
public class CourseBudgetJpaEntity {

    @Id
    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "available_points", nullable = false)
    private int availablePoints;

    @Column(name = "frozen_points", nullable = false)
    private int frozenPoints;

    @Version
    private long version;

    protected CourseBudgetJpaEntity() {
    }

    public CourseBudgetJpaEntity(long courseId, int availablePoints, int frozenPoints) {
        this.courseId = courseId;
        this.availablePoints = availablePoints;
        this.frozenPoints = frozenPoints;
    }

    public Long getCourseId() { return courseId; }
    public int getAvailablePoints() { return availablePoints; }
    public void setAvailablePoints(int availablePoints) { this.availablePoints = availablePoints; }
    public int getFrozenPoints() { return frozenPoints; }
    public void setFrozenPoints(int frozenPoints) { this.frozenPoints = frozenPoints; }
}
