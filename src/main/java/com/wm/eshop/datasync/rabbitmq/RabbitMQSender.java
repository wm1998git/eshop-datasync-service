package com.wm.eshop.datasync.rabbitmq;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** RabbitMQ生产者-负责向用于按维度聚合数据变化的Queue名称： aggr-data-change-queue发送消息 */
@Component
public class RabbitMQSender {

	@Autowired
	private AmqpTemplate rabbitTemplate;

	public void send(String topic, String message) {
		this.rabbitTemplate.convertAndSend(topic, message);
	}

}