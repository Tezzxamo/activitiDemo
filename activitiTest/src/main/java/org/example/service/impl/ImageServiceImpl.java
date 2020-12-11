package org.example.service.impl;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.FlowNode;
import org.activiti.bpmn.model.SequenceFlow;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.runtime.Execution;
import org.example.service.ImageService;
import org.example.service.impl.image.CustomProcessDiagramGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class ImageServiceImpl implements ImageService {

    private Logger logger = LoggerFactory.getLogger(ImageServiceImpl.class);

    private RepositoryService repositoryService;
    private HistoryService historyService;
    private RuntimeService runtimeService;

    @Autowired
    public ImageServiceImpl(RepositoryService repositoryService, HistoryService historyService, RuntimeService runtimeService) {
        this.repositoryService = repositoryService;
        this.historyService = historyService;
        this.runtimeService = runtimeService;
    }

    /**
     * Desc: 通过流程实例ID获取历史流程实例
     *
     * @param processInstanceId 流程实例Id
     * @return 历史流程实例
     */
    public HistoricProcessInstance getHistoricProcessInstance(String processInstanceId) {
        return historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
    }

    /**
     * Desc: 通过流程实例ID获取流程中已经执行的结点，按照执行先后顺序排序
     *
     * @param processInstanceId 流程实例Id
     * @return 已经执行的节点
     */
    public List<HistoricActivityInstance> getHistoricActivityInstancesAsc(String processInstanceId) {
        return historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceId()
                .asc()
                .list();
    }

    /**
     * Desc: 通过流程实例ID获取流程中正在执行的节点
     *
     * @param processInstanceId 流程实例ID
     * @return 正在执行的节点
     */
    public List<Execution> getRunningActivityInstance(String processInstanceId) {
        return runtimeService.createExecutionQuery()
                .processInstanceId(processInstanceId)
                .list();
    }

    /**
     * Desc: 通过流程实例ID获取已经完成的历史流程实例
     *
     * @param processInstanceId 流程实例ID
     * @return 已经完成的历史流程实例
     */
    public List<HistoricProcessInstance> getHistoricFinishedProcessInstance(String processInstanceId) {
        return historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .finished()
                .list();
    }

    /**
     *
     * @param bpmnModel
     * @param historicActivityInstanceList
     * @return
     */
    public List<String> getHighLightedFlows(BpmnModel bpmnModel,
                                            List<HistoricActivityInstance> historicActivityInstanceList) {
        //historicActivityInstanceList 是 流程中已经执行的历史活动实例
        // 已经流经的顺序流，需要高亮显示
        List<String> highFlows = new ArrayList<>();

        // 全部活动节点
        List<FlowNode> allHistoricActivityNodeList = new ArrayList<>();

        // 拥有endTime的历史活动实例，即已经完成了的节点
        List<HistoricActivityInstance> finishedActivityInstancesList = new ArrayList<>();

        /*
         * 循环的目的：
         *           获取所有的历史节点FlowNode并放入allHistoricActivityNodeList
         *           获取所有确定结束了的历史节点finishedActivityInstancesList
         */
        for (HistoricActivityInstance historicActivityInstance : historicActivityInstanceList) {
            // 获取流程节点
            // bpmnModel.getMainProcess()获取一个Process对象
            FlowNode flowNode = (FlowNode) bpmnModel.getMainProcess().getFlowElement(historicActivityInstance.getActivityId(), true);
            allHistoricActivityNodeList.add(flowNode);

            // 如果结束时间不为空，表示当前节点已经完成
            if (historicActivityInstance.getEndTime() != null) {
                finishedActivityInstancesList.add(historicActivityInstance);
            }
        }

        FlowNode currentFlowNode = null;
        FlowNode targetFlowNode = null;
        HistoricActivityInstance currentActivityInstance;

        // 遍历已经完成的活动实例，从每个实例的outgoingFlows中找到已经执行的
        for (int k = 0; k < finishedActivityInstancesList.size(); k++) {
            currentActivityInstance = finishedActivityInstancesList.get(k);

            // 获得当前活动对应的节点信息以及outgoingFlows信息
            currentFlowNode = (FlowNode) bpmnModel.getMainProcess().getFlowElement(currentActivityInstance.getActivityId(), true);

            // 当前节点的所有流出线
            List<SequenceFlow> outgoingFlowList = currentFlowNode.getOutgoingFlows();

            /**
             * 遍历outgoingFlows并找到已经流转的  满足如下条件认为已经流转：
             * 1、当前节点是并行网关或者兼容网关，则通过outgoingFlows能够在历史活动中找到的全部节点均为已经流转
             * 2、当前节点是以上两种类型之外的，通过outgoingFlows查找到的时间最早的流转节点视为有效流转
             * (第二点有问题，有过驳回的，会只绘制驳回的流程线，通过走向下一级的流程线没有高亮显示)
             */
            if ("parallelGateway".equals(currentActivityInstance.getActivityType()) ||
                    "inclusiveGateway".equals(currentActivityInstance.getActivityType())) {
                // 遍历历史活动节点，找到匹配流程目标节点的
                for (SequenceFlow outgoingFlow : outgoingFlowList) {
                    // 获取当前节点流程线对应的下一级节点
                    targetFlowNode = (FlowNode) bpmnModel.getMainProcess().getFlowElement(outgoingFlow.getTargetRef(), true);

                    // 如果下级节点包含在所有历史节点中，则将当前节点的流出线高亮显示
                    if (allHistoricActivityNodeList.contains(targetFlowNode)) {
                        highFlows.add(outgoingFlow.getId());
                    }
                }
            } else {
                /**
                 * 2、当前节点不是并行网关或者兼容网关
                 * 【已解决-问题】如果当前节点有驳回功能，驳回到申请节点，
                 * 则因为申请节点在历史节点中，导致当前节点驳回到申请节点的流程线被高亮显示，但实际并没有进行驳回操作
                 */
                List<Map<String, Object>> tempMapList = new ArrayList<>();

                // 当前节点ID
                String currentActivityId = currentActivityInstance.getActivityId();

                int size = historicActivityInstanceList.size();
                boolean ifStartFind = false;
                boolean ifFinded = false;
                HistoricActivityInstance historicActivityInstance;

                // 循环当前节点的所有流出线
                // 循环所有的历史节点
                logger.info("【开始】-匹配当前节点-ActivityId=【{}】需要高亮显示的流出线", currentActivityId);
                logger.info("循环历史节点");

                for (int i = 0; i < size; i++) {
                    // // 如果当前节点流程线对应的下级节点在历史节点中，则该条流程线进行高亮显示（【问题】有驳回流程线时，即使没有进行驳回操作，因为申请节点在历史节点中，也会将驳回流程线高亮显示-_-||）
                    // if (historicActivityInstance.getActivityId().equals(sequenceFlow.getTargetRef())) {
                    // Map<String, Object> map = new HashMap<>();
                    // map.put("highLightedFlowId", sequenceFlow.getId());
                    // map.put("highLightedFlowStartTime", historicActivityInstance.getStartTime().getTime());
                    // tempMapList.add(map);
                    // // highFlows.add(sequenceFlow.getId());
                    // }

                    // 历史节点
                    historicActivityInstance = historicActivityInstanceList.get(i);
//                    logger.info("第【{}/{}】个历史节点-ActivityId=【{}】", i + 1, size, historicActivityInstance.getActivityId());

                    // 如果循环历史节点中的id等于当前节点id，从当前历史节点继续先后查找是否有当前流程线等于的节点
                    // 历史节点的序号需要大于等于已经完成历史节点的序号，放置驳回重审一个节点经过两次时只取第一次的流出线高亮显示，第二次的不显示
                    if (i >= k && historicActivityInstance.getActivityId().equals(currentActivityId)) {
//                        logger.info("第【{}】个历史节点和当前节点一致-ActivityId=【{}】", i + 1, historicActivityInstance.getActivityId());
                        ifStartFind = true;
                        // 跳过当前节点继续查找下一个节点
                        continue;
                    }
                    if (ifStartFind) {
//                        logger.info("[开始]-循环当前节点-ActivityId=【{}】的所有流出线", currentActivityId);

                        ifFinded = false;
                        for (SequenceFlow sequenceFlow : outgoingFlowList) {
                            // 如果当前节点流程线对应的下级节点在其后面的历史节点中，则该条流程线进行高亮显示
                            // 【问题】
//                            logger.info("当前流出线的下级节点=[{}]", sequenceFlow.getTargetRef());
                            if (historicActivityInstance.getActivityId().equals(sequenceFlow.getTargetRef())) {
//                                logger.info("当前节点[{}]需高亮显示的流出线=[{}]", currentActivityId, sequenceFlow.getId());
                                highFlows.add(sequenceFlow.getId());
                                // 暂时默认找到离当前节点最近的下一级节点即退出循环，否则有多条流出线时将全部被高亮显示
                                ifFinded = true;
                                break;
                            }
                        }
//                        logger.info("[完成]-循环当前节点-ActivityId=【{}】的所有流出线", currentActivityId);
                    }
                    if (ifFinded) {
                        // 暂时默认找到离当前节点最近的下一级节点即退出历史节点循环，否则有多条流出线时将全部被高亮显示
                        break;
                    }
                }
//                logger.info("【完成】-匹配当前节点-ActivityId=【{}】需要高亮显示的流出线", currentActivityId);
                // if (!CollectionUtils.isEmpty(tempMapList)) {
                // // 遍历匹配的集合，取得开始时间最早的一个
                // long earliestStamp = 0L;
                // String highLightedFlowId = null;
                // for (Map<String, Object> map : tempMapList) {
                // long highLightedFlowStartTime = Long.valueOf(map.get("highLightedFlowStartTime").toString());
                // if (earliestStamp == 0 || earliestStamp <= highLightedFlowStartTime) {
                // highLightedFlowId = map.get("highLightedFlowId").toString();
                // earliestStamp = highLightedFlowStartTime;
                // }
                // }
                // highFlows.add(highLightedFlowId);
                // }
            }
        }
        return highFlows;
    }


    @Override
    public InputStream getFlowImgByProcInstId(String procInstId) throws Exception {
        if (StringUtils.isEmpty(procInstId)) {
            logger.error("[错误]-传入的参数procInstId为空！");
            throw new Exception("[异常]-传入的参数procInstId为空！");
        }
        InputStream imageStream = null;
        try {
            // 通过流程实例ID获取历史流程实例
            HistoricProcessInstance historicProcessInstance = getHistoricProcessInstance(procInstId);

            // 通过流程实例ID获取流程中已经执行的节点，按照执行先后顺序排序
            List<HistoricActivityInstance> historicActivityInstanceList = getHistoricActivityInstancesAsc(procInstId);

            // 将已经执行的节点放入高亮显示节点集合
            List<String> highLightedActivityIdList = new ArrayList<>();
            for (HistoricActivityInstance historicActivityInstance : historicActivityInstanceList) {
                highLightedActivityIdList.add(historicActivityInstance.getActivityId());
                logger.info("已执行的节点ID[{}]——活动ID[{}]——活动名称：[{}]——审批人:[{}]", historicActivityInstance.getId(), historicActivityInstance
                        .getActivityId(), historicActivityInstance.getActivityName(), historicActivityInstance
                        .getAssignee());
            }

            // 通过流程实例ID获取流程中正在执行的节点
            List<Execution> runningActivityInstanceList = getRunningActivityInstance(procInstId);
            List<String> runningActivityIdList = new ArrayList<>();
            for (Execution execution : runningActivityInstanceList) {
                if (!StringUtils.isEmpty(execution.getActivityId())) {
                    runningActivityIdList.add(execution.getActivityId());
                    logger.info("执行中的节点[{}-{}-{}]", execution.getId(), execution.getActivityId(), execution.getName());
                }
            }

            // 通过流程实例ID获取已经完成的历史流程实例
            List<HistoricProcessInstance> historicFinishedProcessInstanceList = getHistoricFinishedProcessInstance(procInstId);
            historicActivityInstanceList.forEach(t-> System.out.println(t.getActivityType()));

            // 定义流程画布生成器
            CustomProcessDiagramGenerator processDiagramGenerator = new CustomProcessDiagramGenerator();

            // 获取流程定义Model对象
            BpmnModel bpmnModel = repositoryService.getBpmnModel(historicProcessInstance.getProcessDefinitionId());

            // 获取已经流经的流程线，需要高亮显示流程已经发生流转的线id集合
            List<String> highLightedFlowsIds = getHighLightedFlows(bpmnModel, historicActivityInstanceList);

            // 使用默认配置获得流程图表生成器，并生成追踪图片字符流
            imageStream = processDiagramGenerator.generateDiagramCustom(
                    bpmnModel,
                    highLightedActivityIdList,runningActivityIdList,highLightedFlowsIds
                    ,"宋体","微软雅黑","黑体");
            return imageStream;
        } catch (Exception e) {
            logger.error("通过流程实例ID【{}】获取流程图时出现异常！", e.getMessage());
            throw new Exception("通过流程实例ID" + procInstId + "获取流程图时出现异常！", e);
        } finally {
            if (imageStream != null) {
                imageStream.close();
            }
        }
    }
}
