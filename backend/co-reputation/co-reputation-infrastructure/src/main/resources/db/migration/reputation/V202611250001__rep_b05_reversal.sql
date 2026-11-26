-- B05-2：账本冲正关联列（只追加账本不删除原流水，用新列记录「本条冲正了哪条」）
ALTER TABLE rep_ledger
    ADD COLUMN reverses_event_id VARCHAR(64) NULL
        COMMENT '本条为冲正流水时，指向被冲正原流水的 event_id',
    ADD INDEX idx_ledger_reversal (reverses_event_id);
