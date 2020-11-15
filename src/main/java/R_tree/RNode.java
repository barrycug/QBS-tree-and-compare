package R_tree;


import common.RectItem;
import geometry.*;

import java.util.*;

public abstract class RNode<T extends RectItem> {
	RTree<T> rtree; // 结点所在的树
	int level; // 结点所在的层
	Rectangle MBR; // 相当于条目
	RDirNode<T> parent; // 父节点
	int usedSpace; // 结点已用的空间
	int insertIndex; // 记录插入的搜索路径索引
	int deleteIndex; // 记录删除的查找路径索引

	/**
	 * 构造函数初始化
	 */
	RNode(RTree<T> rtree, RDirNode<T> parent, int level) {
		this.rtree = rtree;
		this.parent = parent;
		this.level = level;
		usedSpace = 0;
	}

	/**
	 * -->删除结点中的第i个条目
	 *
	 */
	abstract void deleteData(int i);

	/**
	 * 压缩算法 叶节点L中刚刚删除了一个条目，如果这个结点的条目数太少下溢， 则删除该结点，同时将该结点中剩余的条目重定位到其他结点中。
	 * 如果有必要，要逐级向上进行这种删除，调整向上传递的路径上的所有外包矩形，使其尽可能小，直到根节点。
	 *
	 * @param list
	 *            存储删除结点中剩余条目
	 */
	void condenseTree(List<RNode<T>> list) {
		if (isRoot()) {
			// 根节点只有一个条目了，即只有左孩子或者右孩子 ，
			// 将唯一条目删除，释放根节点，将原根节点唯一的孩子设置为新根节点
			if (!isLeaf() && usedSpace == 1) {
				RDirNode<T> root = (RDirNode<T>) this;
				RNode<T> child = root.getChild(0);
				root.children[0] = null;
				child.parent = null;
				rtree.setRoot(child);
			}else {
				MBR = getNodeRectangle();
			}
		} else {
			// 计算节点最小容量，用于判断是否引起下溢
			if (usedSpace < rtree.getMinCapacity()) {
				parent.deleteData(parent.deleteIndex); // 其父节点中删除此条目
				list.add(this);// 之前已经把数据删除了
			} else {
				MBR = getNodeRectangle();
			}
			parent.condenseTree(list);
		}
	}

	/**
	 * 分裂结点的平方算法
	 * <p>
	 * 1、为两个组选择第一个条目--调用算法pickSeeds()来为两个组选择第一个元素，分别把选中的两个条目分配到两个组当中。<br>
	 * 2、检查是否已经分配完毕，如果一个组中的条目太少，为避免下溢，将剩余的所有条目全部分配到这个组中，算法终止<br>
	 * 3、调用pickNext来选择下一个进行分配的条目--计算把每个条目加入每个组之后面积的增量，选择两个组面积增量差最大的条目索引,
	 * 如果面积增量相等则选择面积较小的组，若面积也相等则选择条目数更少的组<br>
	 *
	 * @param rectangle
	 *            导致分裂的溢出Rectangle
	 * @return 两个组中的条目的索引
	 */
	int[][] quadraticSplit(Rectangle rectangle) {
		if (rectangle == null)
			throw new IllegalArgumentException("Rectangle cannot be null.");
		Rectangle[] datas = getMBRs(new Rectangle[usedSpace+1]);
		datas[usedSpace] = rectangle; // 先添加进去
		int total = usedSpace + 1; // 结点总数

		// 标记访问的条目
		int[] mask = new int[total];
		for (int i = 0; i < total; i++)
			mask[i] = 1;

		// 分裂后每个组只是有total/2个条目
		int c = total / 2 + 1;
		// 每个结点最小条目个数
		int minNodeSize = rtree.getMinCapacity();

		// 记录没有被检查的条目的个数
		int rem = total;

		int[] group1 = new int[c];// 记录分配的条目的索引
		int[] group2 = new int[c];// 记录分配的条目的索引
		// 跟踪被插入每个组的条目的索引
		int i1 = 0, i2 = 0;

		int[] seed = pickSeeds(datas);
		group1[i1++] = seed[0];
		group2[i2++] = seed[1];
		rem -= 2;
		mask[group1[0]] = -1;
		mask[group2[0]] = -1;

		while (rem > 0) {
			// 将剩余的所有条目全部分配到group1组中，算法终止
			if (minNodeSize - i1 == rem) {
				for (int i = 0; i < total; i++)// 总共rem个
				{
					if (mask[i] != -1)// 还没有被分配
					{
						group1[i1++] = i;
						mask[i] = -1;
						rem--;
					}
				}
				// 将剩余的所有条目全部分配到group1组中，算法终止
			} else if (minNodeSize - i2 == rem) {
				for (int i = 0; i < total; i++)// 总共rem个
				{
					if (mask[i] != -1)// 还没有被分配
					{
						group2[i2++] = i;
						mask[i] = -1;
						rem--;
					}
				}
			} else {
				// 求group1中所有条目的最小外包矩形
				Rectangle mbr1 = datas[group1[0]].clone();
				for (int i = 1; i < i1; i++) {
					mbr1 = mbr1.getUnionRectangle(datas[group1[i]]);
				}
				// 求group2中所有条目的外包矩形
				Rectangle mbr2 = datas[group2[0]].clone();
				for (int i = 1; i < i2; i++) {
					mbr2 = mbr2.getUnionRectangle(datas[group2[i]]);
				}

				// 找出下一个进行分配的条目
				double dif = Double.NEGATIVE_INFINITY;
				double areaDiff1 = 0, areaDiff2 = 0;
				int sel = -1;
				for (int i = 0; i < total; i++) {
					if (mask[i] != -1)// 还没有被分配的条目
					{
						// 计算把每个条目加入每个组之后面积的增量，选择两个组面积增量差最大的条目索引
						Rectangle a = mbr1.getUnionRectangle(datas[i]);
						areaDiff1 = a.getArea() - mbr1.getArea();

						Rectangle b = mbr2.getUnionRectangle(datas[i]);
						areaDiff2 = b.getArea() - mbr2.getArea();

						if (Math.abs(areaDiff1 - areaDiff2) > dif) {
							dif = Math.abs(areaDiff1 - areaDiff2);
							sel = i;
						}
					}
				}

				if (areaDiff1 < areaDiff2)// 先比较面积增量
				{
					group1[i1++] = sel;
				} else if (areaDiff1 > areaDiff2) {
					group2[i2++] = sel;
				} else if (mbr1.getArea() < mbr2.getArea())// 再比较自身面积
				{
					group1[i1++] = sel;
				} else if (mbr1.getArea() > mbr2.getArea()) {
					group2[i2++] = sel;
				} else if (i1 < i2)// 最后比较条目个数
				{
					group1[i1++] = sel;
				} else if (i1 > i2) {
					group2[i2++] = sel;
				} else {
					group1[i1++] = sel;
				}
				mask[sel] = -1;
				rem--;

			}
		} // end while

		int[][] ret = new int[2][];
		ret[0] = new int[i1];
		ret[1] = new int[i2];

		System.arraycopy(group1, 0, ret[0], 0, i1);
		System.arraycopy(group2, 0, ret[1], 0, i2);
		return ret;
	}

	/**
	 * 1、对每一对条目E1和E2，计算包围它们的Rectangle J，计算d = area(J) - area(E1) - area(E2);<br>
	 * 2、Choose the pair with the largest d
	 *
	 * @return 返回两个条目如果放在一起会有最多的冗余空间的条目索引
	 */
	private int[] pickSeeds(Rectangle[] datas) {
		double inefficiency = Double.NEGATIVE_INFINITY;
		int i1 = 0, i2 = 0;

		// 两个for循环对任意两个条目E1和E2进行组合
		for (int i = 0; i < usedSpace; i++) {
			for (int j = i + 1; j <= usedSpace; j++)// 注意此处的j值
			{
				Rectangle rectangle = datas[i].getUnionRectangle(datas[j]);
				double d = rectangle.getArea() - datas[i].getArea() - datas[j].getArea();

				if (d > inefficiency) {
					inefficiency = d;
					i1 = i;
					i2 = j;
				}
			}
		}
		return new int[] { i1, i2 }; // 返回拥有最小d的一对条目
	}

	/**
	 * @return 返回包含结点中所有条目的最小Rectangle
	 */
	Rectangle getNodeRectangle() {
		if (usedSpace > 0) {
			Rectangle[] rectangles = getMBRs(new Rectangle[usedSpace]);
			return Rectangle.getUnionRectangle(rectangles); // 返回包含这一系列矩形的最小矩形
		} else {
			return new Rectangle(new Point(new double[] { 0, 0 }), new Point(new double[] { 0, 0 }));
		}
	}

	public abstract Rectangle[] getMBRs(Rectangle[] rectangles);

	/**
	 * @return 是否根节点
	 */
	boolean isRoot() {
		return parent == null;
	}

	/**
	 * @return 是否叶子结点
	 */
	boolean isLeaf() {
		return (level == 0);
	}

	/**
	 * <b>步骤CL1：</b>初始化――记R树的根节点为N。<br>
	 * <b>步骤CL2：</b>检查叶节点――如果N是个叶节点，返回N<br>
	 * <b>步骤CL3：</b>选择子树――如果N不是叶节点，则从N中所有的条目中选出一个最佳的条目F，
	 * 选择的标准是：如果E加入F后，F的外廓矩形FI扩张最小，则F就是最佳的条目。如果有两个
	 * 条目在加入E后外廓矩形的扩张程度相等，则在这两者中选择外廓矩形较小的那个。<br>
	 * <b>步骤CL4：</b>向下寻找直至达到叶节点――记Fp指向的孩子节点为N，然后返回步骤CL2循环运算， 直至查找到叶节点。
	 * <p>
	 *
	 * @return RTDataNode
	 */
	abstract RNode<T> chooseLeaf(Rectangle rectangle, int level);

	/**
	 * R树的根节点为T，查找包含rectangle的叶子结点
	 * <p>
	 * 1、如果T不是叶子结点，则逐个查找T中的每个条目是否包围rectangle，若包围则递归调用findLeaf()<br>
	 * 2、如果T是一个叶子结点，则逐个检查T中的每个条目能否匹配rectangle<br>
	 *
	 * @return 返回包含rectangle的叶节点
	 */
	abstract RDataNode<T> findLeaf(Elem<T> elem);

	abstract void search(Rectangle queryRect, List<T> list);

	abstract void reInsert();

	boolean check(){
		//MBR check
		Rectangle MBR = getNodeRectangle();
		if (!MBR.equals(this.MBR))
			throw new IllegalArgumentException("error");

		//parent check
		if (isRoot()){
			if (rtree.root != this)
				throw new IllegalArgumentException("error");
		}else {
			boolean flag = false;
			for (int i = 0; i < parent.usedSpace; i++) {
				if (parent.children[i] == this) {
					flag = true;
					break;
				}
			}
			if (!flag)
				throw new IllegalArgumentException("error");
		}
		return true;
	}

	abstract int size();
}