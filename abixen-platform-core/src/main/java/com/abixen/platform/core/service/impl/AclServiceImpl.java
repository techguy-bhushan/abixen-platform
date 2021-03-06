/**
 * Copyright (c) 2010-present Abixen Systems. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.abixen.platform.core.service.impl;

import com.abixen.platform.core.dto.AclPermissionDto;
import com.abixen.platform.core.dto.AclRolePermissionsDto;
import com.abixen.platform.core.dto.AclRolesPermissionsDto;
import com.abixen.platform.core.model.SecurableModel;
import com.abixen.platform.core.model.enumtype.AclSidType;
import com.abixen.platform.core.model.enumtype.PermissionName;
import com.abixen.platform.core.model.impl.*;
import com.abixen.platform.core.repository.*;
import com.abixen.platform.core.repository.custom.AclSidRepository;
import com.abixen.platform.core.service.AclService;
import com.abixen.platform.core.service.PermissionService;
import com.abixen.platform.core.service.RoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;


@Service
public class AclServiceImpl implements AclService {

    private final Logger log = LoggerFactory.getLogger(AclServiceImpl.class);

    @Resource
    private PermissionRepository permissionRepository;

    @Resource
    private AclEntryRepository aclEntryRepository;

    @Resource
    private AclClassRepository aclClassRepository;

    @Resource
    private AclObjectIdentityRepository aclObjectIdentityRepository;

    @Resource
    private PermissionAclClassCategoryRepository permissionAclClassCategoryRepository;

    @Resource
    private AclSidRepository aclSidRepository;

    @Autowired
    private RoleService roleService;

    @Autowired
    private PermissionService permissionService;

    @Override
    public void insertDefaultAcl(SecurableModel securableModel, PermissionName permissionName) {
        List<PermissionName> permissionNames = new ArrayList<>();
        permissionNames.add(permissionName);
        insertDefaultAcl(securableModel, permissionNames);
    }

    @Override
    public void insertDefaultAcl(SecurableModel securableModel, List<PermissionName> permissionNames) {
        AclClass aclClass = aclClassRepository.findByName(securableModel.getClass().getCanonicalName());
        AclSid ownerAclSid = aclSidRepository.findBySidTypeAndSidId(AclSidType.OWNER, 0L);
        AclObjectIdentity aclObjectIdentity = aclObjectIdentityRepository.findByAclClassAndObjectId(aclClass, securableModel.getId());

        if (aclObjectIdentity == null) {
            aclObjectIdentity = new AclObjectIdentity();
            aclObjectIdentity.setAclClass(aclClass);
            aclObjectIdentity.setObjectId(securableModel.getId());
        }

        for (PermissionName permissionName : permissionNames) {
            AclEntry aclEntry = new AclEntry();
            aclEntry.setAclSid(ownerAclSid);
            aclEntry.setPermission(permissionRepository.findByPermissionName(permissionName));
            aclEntry.setAclObjectIdentity(aclObjectIdentity);
            aclEntryRepository.save(aclEntry);
        }
    }

    @Override
    public AclRolesPermissionsDto getAclRolesPermissionsDto(PermissionAclClassCategory permissionAclClassCategory, Long objectId) {
        log.debug("getAclRolesPermissionsDto() - permissionAclClassCategory: " + permissionAclClassCategory + ", objectId: " + objectId);
        List<Permission> permissions = permissionRepository.findAllByPermissionAclClassCategory(permissionAclClassCategory);
        List<Role> roles = roleService.findAllRoles();

        List<AclRolePermissionsDto> aclRolePermissionsDtos = new ArrayList<>();

        for (Role role : roles) {
            AclRolePermissionsDto aclRolePermissionsDto = new AclRolePermissionsDto();
            aclRolePermissionsDto.setRole(role);
            for (Permission permission : permissions) {
                boolean roleHasPermission = role.getPermissions().contains(permission);
                boolean permissionCanBeOverriddenByAcl = !roleHasPermission;
                AclPermissionDto aclPermissionDto = new AclPermissionDto(permission, roleHasPermission, permissionCanBeOverriddenByAcl);
                aclRolePermissionsDto.getAclPermissionDtos().add(aclPermissionDto);
            }
            aclRolePermissionsDtos.add(aclRolePermissionsDto);
        }

        //FIXME
        String aclClassName = permissionAclClassCategory.getAclClass().getName();
        List<AclEntry> aclEntries = aclEntryRepository.findAll(aclClassName, objectId);
        log.debug("aclEntries: " + aclEntries);

        //For owner
        AclRolePermissionsDto aclRoleOwnerPermissionsDto = new AclRolePermissionsDto();
        for (Permission permission : permissions) {
            // boolean ownerHasPermission = aclEntriesContainPermissionForOwner(aclEntries, permission);
            //boolean permissionCanBeOverriddenByAcl = true;
            AclPermissionDto aclPermissionDto = new AclPermissionDto(permission, false, true);
            aclRoleOwnerPermissionsDto.getAclPermissionDtos().add(aclPermissionDto);
        }
        aclRolePermissionsDtos.add(aclRoleOwnerPermissionsDto);

        for (AclRolePermissionsDto aclRolePermissionsDto : aclRolePermissionsDtos) {
            Long roleId = null;
            if (aclRolePermissionsDto.getRole() != null) {
                roleId = aclRolePermissionsDto.getRole().getId();
            }
            List<AclEntry> filteredAclEntries = filterAclEntriesByRoleName(aclEntries, roleId);
            for (AclPermissionDto aclPermissionDto : aclRolePermissionsDto.getAclPermissionDtos()) {
                for (AclEntry aclEntry : filteredAclEntries) {
                    if (aclPermissionDto.getPermission().equals(aclEntry.getPermission())) {
                        aclPermissionDto.setSelected(true);
                    }
                }
            }
        }

        AclRolesPermissionsDto aclRolesPermissionsDto = new AclRolesPermissionsDto(aclRolePermissionsDtos, permissions);
        return aclRolesPermissionsDto;
    }

    @Override
    public AclRolesPermissionsDto getAclRolesPermissionsDto(String permissionAclClassCategoryName, Long objectId) {
        log.debug("getAclRolesPermissionsDto() - permissionAclClassCategoryName: " + permissionAclClassCategoryName + ", objectId: " + objectId);

        PermissionAclClassCategory permissionAclClassCategory = permissionAclClassCategoryRepository.findByName(permissionAclClassCategoryName);

        return getAclRolesPermissionsDto(permissionAclClassCategory, objectId);
    }

    @Transactional
    @Override
    public AclRolesPermissionsDto updateAclRolesPermissionsDto(AclRolesPermissionsDto aclRolesPermissionsDto, String permissionAclClassCategoryName, Long objectId) {
        log.debug("updateAclRolesPermissionsDto() - aclRolesPermissionsDto: " + aclRolesPermissionsDto + ", permissionAclClassCategoryName: " + permissionAclClassCategoryName + ", objectId: " + objectId);

        PermissionAclClassCategory permissionAclClassCategory = permissionAclClassCategoryRepository.findByName(permissionAclClassCategoryName);

        for (AclRolePermissionsDto aclRolePermissionsDto : aclRolesPermissionsDto.getAclRolePermissionsDtos()) {
            log.debug("aclRolePermissionsDto: " + aclRolePermissionsDto.getRole());

            for (AclPermissionDto aclPermissionDto : aclRolePermissionsDto.getAclPermissionDtos()) {
                log.debug("aclPermissionDto - selected: " + aclPermissionDto.isSelected() + ", selectable: " + aclPermissionDto.isSelectable());
            }
        }

        //FIXME
        String aclClassName = permissionAclClassCategory.getAclClass().getName();
        AclClass aclClass = aclClassRepository.findByName(aclClassName);

        List<Role> roles = roleService.findAllRoles();

        for (AclRolePermissionsDto aclRolePermissionsDto : aclRolesPermissionsDto.getAclRolePermissionsDtos()) {
            List<Long> newPermissionIds = new ArrayList<>();
            Role checkedRole = null;
            for (Role role : roles) {

                if (aclRolePermissionsDto.getRole() != null) {
                    if (role.getId() == aclRolePermissionsDto.getRole().getId()) {
                        checkedRole = role;
                    }
                }

            }

            for (AclPermissionDto aclPermissionDto : aclRolePermissionsDto.getAclPermissionDtos()) {
                //if (!aclPermissionDto.getPermission().getPermissionDomainType().equals(permissionDomainType)) {
                //TODO
                //throw Runtime Exception - edit wrong permission domain type
                //}

                if (checkedRole != null) {
                    boolean roleHasPermission = checkedRole.getPermissions().contains(aclPermissionDto.getPermission());

                    if (!roleHasPermission) {
                        if (aclPermissionDto.isSelected()) {
                            newPermissionIds.add(aclPermissionDto.getPermission().getId());
                        }

                    }
                } else {
                    if (aclPermissionDto.isSelected()) {
                        newPermissionIds.add(aclPermissionDto.getPermission().getId());
                    }
                }


            }


            AclObjectIdentity aclObjectIdentity = aclObjectIdentityRepository.findByAclClassAndObjectId(aclClass, objectId);
            AclSid aclSid = aclSidRepository.findBySidTypeAndSidId(checkedRole != null ? AclSidType.ROLE : AclSidType.OWNER, checkedRole != null ? checkedRole.getId() : 0);

            aclEntryRepository.removeAclEntries(aclObjectIdentity, aclSid);

            for (Long permissionId : newPermissionIds) {
                AclEntry aclEntry = new AclEntry();
                aclEntry.setAclObjectIdentity(aclObjectIdentity);
                aclEntry.setPermission(permissionService.findPermission(permissionId));
                aclEntry.setAclSid(aclSid);
                aclEntryRepository.save(aclEntry);
            }

        }

        return getAclRolesPermissionsDto(permissionAclClassCategory, objectId);
    }

    /*private boolean aclEntriesContainPermissionForOwner(List<AclEntry> aclEntries, Permission permission) {
        for (AclEntry aclEntry : aclEntries) {
            if (aclEntry.getAclSid().getSidType().equals(AclSidType.OWNER) && aclEntry.getPermission().equals(permission)) {
                return true;
            }
        }
        return false;
    }*/

    private List<AclEntry> filterAclEntriesByRoleName(List<AclEntry> aclEntries, Long roleId) {
        List<AclEntry> filteredAclEntries = new ArrayList<>();

        if (roleId != null) {
            for (AclEntry aclEntry : aclEntries) {
                if (aclEntry.getAclSid().getSidType().equals(AclSidType.ROLE) && aclEntry.getAclSid().getSidId() == roleId) {
                    filteredAclEntries.add(aclEntry);
                }
            }
        } else {
            for (AclEntry aclEntry : aclEntries) {
                if (aclEntry.getAclSid().getSidType().equals(AclSidType.OWNER)) {
                    filteredAclEntries.add(aclEntry);
                }
            }
        }

        return filteredAclEntries;
    }


}
