package org.example.service.impl;


import org.example.manager.ProcessConfigManager;
import org.example.service.ProcessConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * functions:
 * ①挂起流程定义（普通、级联）
 * ②激活流程定义（普通、级联）
 * ③查询流程定义状态（单个、所有）
 * ④检查流程定义是否存在
 */
@Service
public class ProcessConfigServiceImpl implements ProcessConfigService {

    @Autowired
    ProcessConfigManager processConfigManager;

    /**
     * 将该流程定义挂起，则之后创建流程实例时会失败
     *
     * @param processDefinitionName 流程定义name
     * @return 挂起状态 - > false
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean suspendProcessDefinitionByName(String processDefinitionName) {
        return processConfigManager.suspendProcessDefinitionByName(processDefinitionName);
    }

    /**
     * 将该流程定义挂起，则之后创建流程实例时会失败
     * 若是级联挂起，则之前创建的流程实例也会被挂起
     * 同时可以自定义什么时间开始挂起这个流程定义的时间，若null则立即挂起
     *
     * @param processDefinitionName 流程定义name
     * @param cascade               是否级联挂起
     * @param suspensionDate        多久之后开始挂起
     * @return 挂起状态 - > false
     */
    @Override
    public boolean cascadeSuspendProcessDefinitionByName(String processDefinitionName, boolean cascade, Date suspensionDate) {
        return processConfigManager.cascadeSuspendProcessDefinitionByName(processDefinitionName, cascade, suspensionDate);
    }

    /**
     * 将该流程定义激活，则之后创建流程实例时会成功
     *
     * @param processDefinitionName 流程定义name
     * @return 激活状态 - > true
     */
    @Override
    public boolean activateProcessDefinitionByName(String processDefinitionName) {
        return processConfigManager.activateProcessDefinitionByName(processDefinitionName);
    }

    /**
     * 将该流程定义激活，则之后创建流程实例时会成功
     * 若是级联激活，则有关该流程定义的被挂起的流程实例也会被激活
     * 同时可以自定义什么时间开始激活这个流程定义的时间，若null则立即激活
     *
     * @param processDefinitionName 流程定义name
     * @param cascade               是否级联激活
     * @param activationDate        多久之后开始激活
     * @return 激活状态 - > true
     */
    @Override
    public boolean cascadeActivateProcessDefinitionByName(String processDefinitionName, boolean cascade, Date activationDate) {
        return processConfigManager.cascadeActivateProcessDefinitionByName(processDefinitionName,cascade,activationDate);
    }

    /**
     * @param processDefinitionName 流程定义name
     * @return 激活状态返回 - > true , 挂起状态返回 - > false
     */
    @Override
    public boolean getProcessDefinitionStatusByName(String processDefinitionName) {
        return processConfigManager.getProcessDefinitionStatusByName(processDefinitionName);
    }

    /**
     * @return 返回所有流程定义的状态（挂起或激活）
     */
    @Override
    public Map<String, Boolean> getProcessConfigStatusMap() {
        return processConfigManager.getProcessConfigStatusMap();
    }

}
