-- 1. 给原有的 ypc_sku_info 表增加 status 字段（如果尚未添加）
ALTER TABLE ypc_sku_info
ADD COLUMN status TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1: 在售, 0: 下架' AFTER last_sync_time;

-- 2. 创建商品价格历史表
CREATE TABLE IF NOT EXISTS ypc_sku_price_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    sku_id VARCHAR(64) NOT NULL COMMENT '商品SKU ID',
    price DECIMAL(10, 2) NOT NULL COMMENT '记录时的价格',
    create_time DATETIME NOT NULL COMMENT '记录时间',
    INDEX idx_sku_id (sku_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品价格走势历史表';