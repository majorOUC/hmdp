-- 创建预约表
DROP TABLE IF EXISTS `reservation`;
CREATE TABLE `reservation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `name` varchar(50) NOT NULL COMMENT '用户姓名',
  `phone` varchar(20) NOT NULL COMMENT '用户手机号',
  `communication_time` datetime NOT NULL COMMENT '预约到店时间',
  `shop_name` varchar(100) NOT NULL COMMENT '预约商家名称',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约到店消费表';
