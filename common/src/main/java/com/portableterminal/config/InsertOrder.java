package com.portableterminal.config;

/** 存入时容器的遍历顺序。 */
public enum InsertOrder {
	/** 离玩家近的先装。 */
	NEAREST,
	/** 离玩家远的先装。 */
	FARTHEST,
	/** 按坐标升序（X → Y → Z），与旧版扫描顺序一致。 */
	COORDINATES
}
