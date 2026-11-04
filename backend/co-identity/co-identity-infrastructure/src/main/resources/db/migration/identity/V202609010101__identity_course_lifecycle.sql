-- B03：课程生命周期与成员软删除
-- id_course 增加状态（OPEN/ARCHIVED）；id_course_member 增加软删除状态与移除时间。
-- 已有数据默认 OPEN / ACTIVE，无需回填。

ALTER TABLE id_course
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN-进行中 ARCHIVED-已归档（只读）';

ALTER TABLE id_course_member
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE-有效 REMOVED-已移除（软删除）',
    ADD COLUMN removed_at DATETIME(6) NULL COMMENT '移除时间';

CREATE INDEX idx_member_course_status ON id_course_member (course_id, status);
