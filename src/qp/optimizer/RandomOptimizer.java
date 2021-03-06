/* performs randomized optimization, iterative improvement algorithm  */
package qp.optimizer;

import qp.utils.*;
import qp.operators.*;

import java.util.Vector;

public class RandomOptimizer {

    /* enumeration of different ways to find the neighbor plan */
    public static final int METHODCHOICE = 0;  //selecting neighbor by changing a method for an operator
    public static final int COMMUTATIVE = 1;   // by rearranging the operators by commutative rule
    public static final int ASSOCIATIVE = 2;  // rearranging the operators by associative rule

    /* Number of alternative methods available for a node as specified above */
    public static final int NUMCHOICES = 3;

    SQLQuery sqlquery;     // Vector of Vectors of Select + From + Where + GroupBy
    int numJoin;          // Number of joins in this query plan

    public RandomOptimizer(SQLQuery sqlquery) {
        this.sqlquery = sqlquery;
    }

    /**
     * Randomly selects a neighbour
     **/
    protected Operator getNeighbor(Operator root) {
        //Randomly select a node to be altered to get the neighbour
        int nodeNum = RandNumb.randInt(0, numJoin - 1);

        //Randomly select type of alteration: Change Method/Associative/Commutative
        int changeType = RandNumb.randInt(0, NUMCHOICES - 1);
        Operator neighbor = null;

        switch (changeType) {
            case METHODCHOICE:   // Select a neighbour by changing the method type
                neighbor = neighborMeth(root, nodeNum);
                break;
            case COMMUTATIVE:
                neighbor = neighborCommut(root, nodeNum);
                break;
            case ASSOCIATIVE:
                neighbor = neighborAssoc(root, nodeNum);
                break;
        }
        return neighbor;
    }

    /**
     * implementation of Iterative Improvement Algorithm for Randomized optimization of Query Plan
     **/
    public Operator getOptimizedPlan() {
        RandomInitialPlan rip = new RandomInitialPlan(sqlquery);
        numJoin = rip.getNumJoins();

        int MINCOST = Integer.MAX_VALUE;
        Operator finalPlan = null;

        /* NUMITER is number of times random restart */

        int NUMITER;
        if (numJoin != 0) {
            NUMITER = 2 * numJoin;
        } else {
            NUMITER = 1;
        }

        /* Randomly restart the gradient descent until the maximum specified number of random restarts (NUMITER) has satisfied */

        for (int j = 0; j < NUMITER; j++) {
            Operator initPlan = rip.prepareInitialPlan();

            modifySchema(initPlan);
            Debug.printWithLines(false, "initial plan");
            Debug.PPrint(initPlan);
            PlanCost pc = new PlanCost();
            int initCost = pc.getCost(initPlan);
            System.out.println(initCost);

            boolean flag = true;
            int minNeighborCost = initCost; //just initialization purpose;
            Operator minNeighbor = initPlan; //just initialization purpose;
            if (numJoin != 0) {

                while (flag) {   // flag = false when local minimum is reached
                    Debug.printWithLines(false, "while");
                    Operator initPlanCopy = (Operator) initPlan.clone();
                    minNeighbor = getNeighbor(initPlanCopy);

                    Debug.printWithLines(false, "neighbour");
                    Debug.PPrint(minNeighbor);
                    pc = new PlanCost();
                    minNeighborCost = pc.getCost(minNeighbor);
                    System.out.println("  " + minNeighborCost);

                    /* In this loop we consider from the possible neighbors (randomly selected) and take the minimum among for next step */

                    for (int i = 1; i < 2 * numJoin; i++) {
                        initPlanCopy = (Operator) initPlan.clone();
                        Operator neighbor = getNeighbor(initPlanCopy);
                        Debug.printWithLines(false, "neighbour");
                        Debug.PPrint(neighbor);
                        pc = new PlanCost();
                        int neighborCost = pc.getCost(neighbor);
                        System.out.println(neighborCost);


                        if (neighborCost < minNeighborCost) {
                            minNeighbor = neighbor;
                            minNeighborCost = neighborCost;
                        }
                    }
                    if (minNeighborCost < initCost) {
                        initPlan = minNeighbor;
                        initCost = minNeighborCost;
                    } else {
                        minNeighbor = initPlan;
                        minNeighborCost = initCost;

                        flag = false;   // local minimum reached
                    }
                }
                Debug.printWithLines(false, "local minimum");
                Debug.PPrint(minNeighbor);
                System.out.println(" " + minNeighborCost);

            }
            if (minNeighborCost <= MINCOST) { // modified < to <=
                MINCOST = minNeighborCost;
                finalPlan = minNeighbor;
            }
        }
        System.out.println("\n\n\n");
        Debug.printWithLines(false, "final plan");
        Debug.PPrint(finalPlan);
        System.out.println("  " + MINCOST);
        return finalPlan;
    }


    /** Selects a random method choice for join with number joinNum
     *  e.g., Nested loop join, Sort-Merge Join, Hash Join etc..,
     * @return the modified plan
     **/
    protected Operator neighborMeth(Operator root, int joinNum) {
        Debug.printWithLines(false, "neighbour by method change");
        int numJMeth = JoinType.numJoinTypes();
        if (numJMeth > 1) {
            /* find the node that is to be altered */
            Join node = (Join) findNodeAt(root, joinNum);
            int prevJoinMeth = node.getJoinType();
            int joinMeth = RandNumb.randInt(0, numJMeth - 1);
            while (joinMeth == prevJoinMeth) {
                joinMeth = RandNumb.randInt(0, numJMeth - 1);
            }
            node.setJoinType(joinMeth);
        }
        return root;
    }

    /**
     * Applies join Commutativity for the join numbered with joinNum e.g.,  A X B is changed as B X A
     * @return the modified plan
     **/
    protected Operator neighborCommut(Operator root, int joinNum) {
        Debug.printWithLines(false, "neighbour by commutative");
        /*  find the node to be altered */
        Join node = (Join) findNodeAt(root, joinNum);
        Operator left = node.getLeft();
        Operator right = node.getRight();
        node.setLeft(right);
        node.setRight(left);
        /* also flip the condition i.e.,  A X a1b1 B   = B X b1a1 A */
        node.getCondition().flip();

        /* modify the schema before returning the root */
        modifySchema(root);
        return root;
    }

    /**
     * Applies join Associativity for the join numbered with joinNum e.g., (A X B) X C is changed to A X (B X C)
     *  @return the modified plan
     **/
    protected Operator neighborAssoc(Operator root, int joinNum) {
        /* find the node to be altered */
        Join op = (Join) findNodeAt(root, joinNum);
        Operator left = op.getLeft();
        Operator right = op.getRight();

        if (left.getOpType() == OpType.JOIN && right.getOpType() != OpType.JOIN) {
            transformLefttoRight(op, (Join) left);

        } else if (left.getOpType() != OpType.JOIN && right.getOpType() == OpType.JOIN) {
            transformRighttoLeft(op, (Join) right);
        } else if (left.getOpType() == OpType.JOIN && right.getOpType() == OpType.JOIN) {
            if (RandNumb.flipCoin())
                transformLefttoRight(op, (Join) left);
            else
                transformRighttoLeft(op, (Join) right);
        } else {
            // The join is just A X B,  therefore Association rule is not applicable
        }

        /** modify the schema before returning the root **/
        modifySchema(root);
        return root;
    }


    /** This is given plan (A X B) X C **/
    protected void transformLefttoRight(Join op, Join left) {
        Debug.printWithLines(false, "left to right neighbour");
        Operator right = op.getRight();
        Operator leftleft = left.getLeft();
        Operator leftright = left.getRight();
        Attribute leftAttr = op.getCondition().getLhs();
        Join temp;

        /* CASE 1 : ( A X a1b1 B) X b4c4  C     =  A X a1b1 (B X b4c4 C) a1b1,  b4c4 are the join conditions at that join operator */

        if (leftright.getSchema().contains(leftAttr)) {
            Debug.printWithLines(false, "CASE 1");
            temp = new Join(leftright, right, op.getCondition(), OpType.JOIN);
            temp.setJoinType(op.getJoinType());
            temp.setNodeIndex(op.getNodeIndex());
            op.setLeft(leftleft);
            op.setJoinType(left.getJoinType());
            op.setNodeIndex(left.getNodeIndex());
            op.setRight(temp);
            op.setCondition(left.getCondition());

        } else {
            Debug.printWithLines(false, "CASE 2");
            /**CASE 2:   ( A X a1b1 B) X a4c4  C     =  B X b1a1 (A X a4c4 C)
             ** a1b1,  a4c4 are the join conditions at that join operator
             **/
            temp = new Join(leftleft, right, op.getCondition(), OpType.JOIN);
            temp.setJoinType(op.getJoinType());
            temp.setNodeIndex(op.getNodeIndex());
            op.setLeft(leftright);
            op.setRight(temp);
            op.setJoinType(left.getJoinType());
            op.setNodeIndex(left.getNodeIndex());
            Condition newcond = left.getCondition();
            newcond.flip();
            op.setCondition(newcond);
        }
    }

    protected void transformRighttoLeft(Join op, Join right) {
        Debug.printWithLines(false, "right to left neighbour");
        Operator left = op.getLeft();
        Operator rightleft = right.getLeft();
        Operator rightright = right.getRight();
        Attribute rightAttr = (Attribute) op.getCondition().getRhs();
        Join temp;
        /** CASE 3 :  A X a1b1 (B X b4c4  C)     =  (A X a1b1 B ) X b4c4 C
         ** a1b1,  b4c4 are the join conditions at that join operator
         **/
        if (rightleft.getSchema().contains(rightAttr)) {
            Debug.printWithLines(false, "CASE 3");
            temp = new Join(left, rightleft, op.getCondition(), OpType.JOIN);
            temp.setJoinType(op.getJoinType());
            temp.setNodeIndex(op.getNodeIndex());
            op.setLeft(temp);
            op.setRight(rightright);
            op.setJoinType(right.getJoinType());
            op.setNodeIndex(right.getNodeIndex());
            op.setCondition(right.getCondition());
        } else {
            /* CASE 4 :  A X a1c1 (B X b4c4  C)     =  (A X a1c1 C ) X c4b4 B
             * a1b1,  b4c4 are the join conditions at that join operator
             **/
            Debug.printWithLines(false, "CASE 4");
            temp = new Join(left, rightright, op.getCondition(), OpType.JOIN);
            temp.setJoinType(op.getJoinType());
            temp.setNodeIndex(op.getNodeIndex());

            op.setLeft(temp);
            op.setRight(rightleft);
            op.setJoinType(right.getJoinType());
            op.setNodeIndex(right.getNodeIndex());
            Condition newcond = right.getCondition();
            newcond.flip();
            op.setCondition(newcond);
        }
    }

    /**
     * This method traverses through the query plan and returns the node mentioned by joinNum
     **/
    protected Operator findNodeAt(Operator node, int joinNum) {
        if (node.getOpType() == OpType.JOIN) {
            if (((Join) node).getNodeIndex() == joinNum) {
                return node;
            } else {
                Operator temp;
                temp = findNodeAt(((Join) node).getLeft(), joinNum);
                if (temp == null)
                    temp = findNodeAt(((Join) node).getRight(), joinNum);
                return temp;
            }
        } else if (node.getOpType() == OpType.SCAN) {
            return null;
        } else if (node.getOpType() == OpType.SELECT) {
            //if sort/project/select operator
            return findNodeAt(((Select) node).getBase(), joinNum);
        } else if (node.getOpType() == OpType.PROJECT) {
            return findNodeAt(((Project) node).getBase(), joinNum);
        } else if (node.getOpType() == OpType.ORDERBY) {
        	return findNodeAt(((OrderBy) node).getBase(), joinNum);
        }
        else {
            return null;
        }
    }

    /**
     * modifies the schema of operators which are modified due to selecting an alternative neighbor plan
     **/
    private void modifySchema(Operator node) {
        // the base case is of OpType == OpType.SCAN
        if (node.getOpType() == OpType.JOIN) {
            Operator left = ((Join) node).getLeft();
            Operator right = ((Join) node).getRight();
            modifySchema(left);
            modifySchema(right);
            node.setSchema(left.getSchema().joinWith(right.getSchema()));
        } else if (node.getOpType() == OpType.SELECT) {
            Operator base = ((Select) node).getBase();
            modifySchema(base);
            node.setSchema(base.getSchema());
        } else if (node.getOpType() == OpType.PROJECT) {
            Operator base = ((Project) node).getBase();
            modifySchema(base);
            Vector attrlist = ((Project) node).getProjAttr();
            node.setSchema(base.getSchema().subSchema(attrlist));
        }
    }

    /** After finding a choice of method for each operator, prepare an execution plan by replacing the methods with corresponding join operator implementation
     */
    public static Operator makeExecPlan(Operator node) {
    	if (node.getOpType() == OpType.JOIN) {
            Operator left = makeExecPlan(((Join) node).getLeft());
            Operator right = makeExecPlan(((Join) node).getRight());
            int joinType = ((Join) node).getJoinType();
            int numbuff = BufferManager.getBuffersPerJoin();
            Join joinOperator;
            switch (joinType) {
                case JoinType.NESTEDJOIN:
                    joinOperator = new NestedJoin((Join) node);
                    break;
                case JoinType.HASHJOIN:
                case JoinType.SORTMERGE:
                	joinOperator = new SortMerge((Join) node);
                	break;
                case JoinType.BLOCKNESTED:
                    joinOperator = new BlockNestedJoin((Join) node);
                    break;
                default:
                    return node;
            }
            joinOperator.setLeft(left);
            joinOperator.setRight(right);
            joinOperator.setNumBuff(numbuff);
            return joinOperator;
        } else if (node.getOpType() == OpType.SELECT) {
            Operator base = makeExecPlan(((Select) node).getBase());
            ((Select) node).setBase(base);
            return node;
        } else if (node.getOpType() == OpType.PROJECT) {
            Operator base = makeExecPlan(((Project) node).getBase());
            ((Project) node).setBase(base);
            return node;
        } else if (node.getOpType() == OpType.ORDERBY) {
        	OrderBy ob = (OrderBy) node;
        	Operator base = makeExecPlan(ob.getBase());
        	ob.setBase(base);
        	int numbuff = BufferManager.getBuffers();
        	ob.setNumBuff(numbuff);
            return ob;
        } else {
            return node;
        }
    }
}