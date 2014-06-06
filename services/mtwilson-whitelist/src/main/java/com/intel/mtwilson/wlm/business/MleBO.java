/*
 * Copyright (C) 2013 Intel Corporation
 * All rights reserved.
 */
package com.intel.mtwilson.wlm.business;

import com.intel.mtwilson.i18n.ErrorCode;
import com.intel.mountwilson.as.common.ASException;
import com.intel.mtwilson.as.controller.*;
import com.intel.mtwilson.as.controller.exceptions.ASDataException;
import com.intel.mtwilson.as.controller.exceptions.NonexistentEntityException;
import com.intel.mtwilson.as.data.*;
import com.intel.mtwilson.datatypes.*;
import com.intel.mtwilson.wlm.helper.BaseBO;
import com.intel.dcsg.cpg.io.UUID;
import com.intel.mtwilson.My;

import java.util.*;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author dsmagadx
 */
public class MleBO extends BaseBO {

        Logger log = LoggerFactory.getLogger(getClass().getName());
        TblMleJpaController mleJpaController = null;
        TblPcrManifestJpaController pcrManifestJpaController = null;
        TblModuleManifestJpaController moduleManifestJpaController = null;
        TblEventTypeJpaController eventTypeJpaController = null;
        TblPackageNamespaceJpaController packageNSJpaController = null;
        private static String hexadecimalRegEx = "[0-9A-Fa-f]{40}";  // changed from + to 40 because sha1 is always 40 characters long when it's in hex
        private static String invalidWhiteList = "[0]{40}|[Ff]{40}";

        public MleBO() {
                                mleJpaController = new TblMleJpaController(getEntityManagerFactory());
                                pcrManifestJpaController = new TblPcrManifestJpaController(getEntityManagerFactory());
                                moduleManifestJpaController = new TblModuleManifestJpaController(getEntityManagerFactory());
                                eventTypeJpaController = new TblEventTypeJpaController(getEntityManagerFactory());
                                packageNSJpaController = new TblPackageNamespaceJpaController(getEntityManagerFactory());
        }

                        // This function will be used to validate the white list values. We have seen in some cases where in we would get -1. 
                        private boolean isWhiteListValid(String whiteList) {
                            if( whiteList == null || whiteList.trim().isEmpty() ) { return true; } // we allow empty values because in mtwilson 1.2 they are used to indicate dynamic information, for example vmware pcr 19, and the command line event that is extended into vmware pcr 19
                            // Bug:775 & 802: If the TPM is reset we have seen that all the PCR values would be set to Fs. So, we need to disallow that since it is invalid. Also, all 0's are also invalid.
                           if (whiteList.matches(invalidWhiteList)) {return false;}
                                if (whiteList.matches(hexadecimalRegEx)) {
                                        return true;
                                } else {
                                    return false;
                                }
                        }
                        
                        private void validateWhitelistValue(String componentName, String whiteList) {
                            if(!isWhiteListValid(whiteList)) {
                                log.error("White list '{}' specified for '{}' is not valid.", whiteList, componentName);
                                throw new ASException(ErrorCode.WS_INVALID_WHITE_LIST_VALUE, whiteList, componentName);
                            }
                        }
        
                        // @since 1.1 we are relying on the audit log for "created on", "created by", etc. type information
                        /*
                        private TblDbPortalUser getLoggedInUser() {
                            if( loggedInUser == null ) {
                                loggedInUser = dbPortalUserJpaController.findTblDbPortalUser(1);
                            }
                            return loggedInUser;
                        }*/
        
	/**
	 * For VMM, the OS Name and OS Version in the new MLE must ALREADY be in the
	 * database, or this method will throw an error.
	 * 
	 * @param mleData
	 * @return
	 */
	public String addMLe(MleData mleData, String mleUuid) {
            String osOemUuid = null;
                                try {
                                    log.debug("add mle type: {}", mleData.getMleType());
                                    log.debug("add mle name: {}", mleData.getName());
                                    log.debug("add mle version: {}", mleData.getVersion());
                                    log.debug("add mle os name: {}", mleData.getOsName());
                                    log.debug("add mle os version: {}", mleData.getOsVersion());
                                    log.debug("add mle oem: {}", mleData.getOemName());
                                    log.debug("add mle attestation type: {}", mleData.getAttestationType());

                                    TblMle tblMle = getMleDetails(mleData.getName(),
                                                        mleData.getVersion(), mleData.getOsName(),
                                                        mleData.getOsVersion(), mleData.getOemName());

                                        if (tblMle != null) {
                                                throw new ASException(ErrorCode.WS_MLE_ALREADY_EXISTS, mleData.getName());
                                        }

                                        // XXX TODO this needs to be refactored into vendor-specific checks
                                        // This check has been moved to the VMware Host agent.
                                        /*if(mleData.getName().toUpperCase().contains("ESX")){
                                                String version = getUpperCase(mleData.getVersion()).substring(0, 2);
                                                if(!version.equals("51") && !version.equals("50")){
                                                        throw new ASException(ErrorCode.WS_ESX_MLE_NOT_SUPPORTED);
                                                }
                                        }*/
                                        
                                        // If the mleUuid is not specified a new one would be created. But we need to get the reference
                                        // to either BIOS or VMM UUID from the DB.
                                        if (mleData.getMleType().equals("VMM")) {
                                            TblOs osObj = My.jpa().mwOs().findTblOsByNameVersion(mleData.getOsName(), mleData.getOsVersion());
                                            osOemUuid = osObj.getUuid_hex();
                                        } else if (mleData.getMleType().equals("BIOS")) {
                                            TblOem oemObj = My.jpa().mwOem().findTblOemByName(mleData.getOemName());
                                            osOemUuid = oemObj.getUuid_hex();
                                        }

                                        tblMle = getTblMle(mleData, mleUuid, osOemUuid);
                                                                                
                                        // before we create the MLE, check that the provided PCR values are valid -- if they aren't we abort
                                        if( mleData.getManifestList() != null ) {
                                            for(ManifestData pcrData : mleData.getManifestList()) {
                                                validateWhitelistValue(pcrData.getName(), pcrData.getValue());
                                            }
                                        }
                                        mleJpaController.create(tblMle);
                                        // now add the PCRs that were validated above
                                        addPcrManifest(tblMle, mleData.getManifestList(), null, null);

                                } catch (ASException ase) {
                                    //log.error("Exception while adding MLE data." + ase.getErrorMessage());
                                    throw ase;
                                } catch (Exception e) {
                                                        //log.error("Error while adding MLE data. " + e.getMessage());            
                                        //                throw new ASException(ErrorCode.SYSTEM_ERROR, String.format("Error while adding MLE '%s'. %s", 
                                        //                        mleData.getName(), e.getMessage()), e);
                                    // throw new ASException(e);
                                    log.error("Error during MLE creation.", e);
                                    throw new ASException(ErrorCode.WS_MLE_CREATE_ERROR, e.getClass().getSimpleName());            
                                }

                                return "true";
	}
	
                        /**
                         * 
                         * @param str
                         * @return 
                         */
	/*private String getUpperCase(String str) {
                                if(str != null){
                                        return str.toUpperCase().replaceAll("[/.]","");
                                }
                                return "NULL";
	}*/


                        /**
                         * 
                         * @param mleData
                         * @return 
                         */
	public String updateMle(MleData mleData, String mleUuid) {
            TblMle tblMle;
                                try {
                                    // Feature: 917 - Support for UUID
                                    if (mleUuid != null && !mleUuid.isEmpty()) {
                                        tblMle = mleJpaController.findTblMleByUUID(mleUuid);
                                    } else {
                                        tblMle = getMleDetails(mleData.getName(),
                                                        mleData.getVersion(), mleData.getOsName(),
                                                        mleData.getOsVersion(), mleData.getOemName());
                                    }
                                        if (tblMle == null) {
                                                throw new ASException(ErrorCode.WS_MLE_DOES_NOT_EXIST, mleData.getName(), mleData.getVersion());
                                        }

                                        setTblMle(tblMle, mleData);

                                        mleJpaController.edit(tblMle);
                                        updatePcrManifest(tblMle, mleData);

                                } catch (ASException ase) {
                                    throw ase;
                                } catch (Exception e) {
                    //                throw new ASException(ErrorCode.SYSTEM_ERROR, String.format("Error while updating MLE '%s'. %s", 
                    //                        mleData.getName(), e.getMessage()), e);
                                    // new ASException(e);
                                    log.error("Error during MLE update.", e);
                                    throw new ASException(ErrorCode.WS_MLE_UPDATE_ERROR, e.getClass().getSimpleName());            
                                }

                                return "true";
	}

                        /**
                         * 
                         * @param mleName
                         * @param mleVersion
                         * @param osName
                         * @param osVersion
                         * @param oemName
                         * @return 
                         */
	public String deleteMle(String mleName, String mleVersion, String osName, String osVersion, String oemName, String mleUuid) {
            TblMle tblMle;
                                try {
                                    if (mleUuid != null && !mleUuid.isEmpty()) {
                                        tblMle = mleJpaController.findTblMleByUUID(mleUuid);
                                    } else {
                                        tblMle = getMleDetails(mleName, mleVersion, osName, osVersion, oemName);
                                    }

                                    if (tblMle == null) {
                                            throw new ASException(ErrorCode.WS_MLE_DOES_NOT_EXIST, mleName, mleVersion);
                                    }

                                    // Bug:438 - Need to check the type of the MLE and accordingly call the function
                                    // to get the associated host list.
                                    Collection<TblHosts> tblHostsCollection;
                                    if (oemName == null || oemName.isEmpty()) {
                                        tblHostsCollection = tblMle.getTblHostsCollection();
                                    } else {
                                        tblHostsCollection = tblMle.getTblHostsCollection1();
                                    }
                                    if( tblHostsCollection != null ) {
                                        //log.debug(String.format("MLE '%s' is currently associated with '%d' hosts. ", mleName, tblHostsCollection.size()));
                                        log.debug("MLE {} is currently associated with {} hosts. ", mleName, tblHostsCollection.size());

                                        if (!tblHostsCollection.isEmpty()) {
                                            throw new ASException(ErrorCode.WS_MLE_ASSOCIATION_EXISTS, mleName, mleVersion, tblHostsCollection.size());
                                        }
                                    }

                                    for(TblModuleManifest moduleManifest : tblMle.getTblModuleManifestCollection()){
                                            moduleManifestJpaController.destroy(moduleManifest.getId());
                                    }


                                    for (TblPcrManifest manifest : tblMle.getTblPcrManifestCollection()) {
                                            pcrManifestJpaController.destroy(manifest.getId());
                                    }

                                    // We also need to delete entries in the MleSource table for the MLE. This table would store the host
                                    // name that was used to white list the MLE.
                                    deleteMleSource(mleName, mleVersion, osName, osVersion, oemName, mleUuid);

                                    mleJpaController.destroy(tblMle.getId());

                                } catch (ASException ase) {
                                    throw ase;
                                } catch (Exception e) {
                    //                throw new ASException(ErrorCode.SYSTEM_ERROR, String.format("Error while deleting MLE '%s'. %s", 
                    //                        mleName, e.getMessage()), e);                
                                    // throw new ASException(e);
                                    log.error("Error during MLE deletion.", e);
                                    throw new ASException(ErrorCode.WS_MLE_DELETE_ERROR, e.getClass().getSimpleName());            
                                }

                                return "true";
	}

                        /**
                         * 
                         * @param searchCriteria
                         * @return 
                         */
	public List<MleData> listMles(String searchCriteria) {
                                List<MleData> mleDataList = new ArrayList<MleData>();

                                List<TblMle> tblMleList;

                                try {
                                    if (searchCriteria != null && !searchCriteria.isEmpty())
                                            tblMleList = mleJpaController .findMleByNameSearchCriteria(searchCriteria);
                                    else
                                            tblMleList = mleJpaController.findTblMleEntities();

                                    if (tblMleList != null) {
                                            // log.debug(String.format("Found [%d] mle results for search criteria [%s]", tblMleList.size(), searchCriteria));
                                            log.debug("Found {} mle results for search criteria {}", tblMleList.size(), searchCriteria);

                                            for (TblMle tblMle : tblMleList) {
                                                    MleData mleData = createMleDataFromDatabaseRecord(tblMle, false);
                                                    mleDataList.add(mleData);
                                            }
                                    } else {
                                            // log.debug(String.format("Found [%d] mle results for search criteria [%s]", 0,searchCriteria));
                                            log.debug("Found {} mle results for search criteria {}", 0,searchCriteria);
                                    }

                                } catch (ASException ase) {
                                        throw ase;
                                } catch (Exception e) {
                    //                    throw new ASException(ErrorCode.SYSTEM_ERROR, String.format("Error while searching for MLEs. %s", e.getMessage()), e);                
                                    // throw new ASException(e);
                                    log.error("Error during retrieval of MLE information.", e);
                                    throw new ASException(ErrorCode.WS_MLE_RETRIEVAL_ERROR, e.getClass().getSimpleName());            
                                }
                                return mleDataList;
	}

                        /**
                         * 
                         * @param mleName
                         * @param mleVersion
                         * @param osName
                         * @param osVersion
                         * @param oemName
                         * @return 
                         */
	public MleData findMle(String mleName, String mleVersion, String osName, String osVersion, String oemName) {
                                try {
                                        TblMle tblMle = getMleDetails(mleName, mleVersion, osName, osVersion, oemName);

                                        if (tblMle == null) {
                                            throw new ASException(ErrorCode.WS_MLE_DOES_NOT_EXIST, mleName, mleVersion);                        
                                        }

                                        MleData mleData = createMleDataFromDatabaseRecord(tblMle, true);
                                        return mleData;

                                } catch (ASException ase) {
                                        throw ase;
                                } catch (Exception e) {
                    //                    throw new ASException(ErrorCode.SYSTEM_ERROR, String.format("Exception while retrieving the MLE details. %s", e.getMessage()), e);                                
                                    // throw new ASException(e);
                                    log.error("Error during retrieval of MLE information.", e);
                                    throw new ASException(ErrorCode.WS_MLE_RETRIEVAL_ERROR, e.getClass().getSimpleName());            
                                }	
	}

                        /**
                         * 
                         * @param mleName
                         * @param mleVersion
                         * @param osName
                         * @param osVersion
                         * @param oemName
                         * @return 
                         */
	private TblMle getMleDetails(String mleName, String mleVersion,	String osName, String osVersion, String oemName) {
                                TblMle tblMle;
                                log.debug(String.format("Mle name '%s' version '%s' os '%s' os version '%s' oem '%s'. ",
                                                mleName, mleVersion, osName, osVersion, oemName));
                                validateNull("mleName", mleName);
                                validateNull("mleVersion", mleVersion);
                                validateMleExtraAttributes(osName, osVersion, oemName);
                                if (StringUtils.isNotBlank(oemName)) {
                                        log.info("Getting BIOS MLE from database");
                                        tblMle = mleJpaController.findBiosMle(mleName, mleVersion, oemName);
                                } else {
                                        log.info("Get VMM MLE from database");
                                        tblMle = mleJpaController.findVmmMle(mleName, mleVersion, osName,osVersion);
                                }
                                return tblMle;
	}

                        /**
                         * 
                         * @param tblMle
                         * @param addManifest
                         * @return 
                         */
	public MleData createMleDataFromDatabaseRecord(TblMle tblMle, boolean addManifest) {
                                List<ManifestData> manifestList = null;

                                if (addManifest) {
                                        manifestList = new ArrayList<>();
                                        for (TblPcrManifest pcrManifest : tblMle.getTblPcrManifestCollection()) {
                                                manifestList.add(new ManifestData(pcrManifest.getName(), pcrManifest.getValue()));
                                        }
                                }

                                String osName = (tblMle.getOsId() == null) ? null : tblMle.getOsId().getName();
                                String osVersion = (tblMle.getOsId() == null) ? null : tblMle.getOsId().getVersion();
                                String oemName = (tblMle.getOemId() == null) ? null : tblMle.getOemId().getName();

                                MleData s = new MleData(tblMle.getName(), tblMle.getVersion(), MleData.MleType.valueOf(tblMle.getMLEType()),
                                                MleData.AttestationType.valueOf(tblMle.getAttestationType()),
                                                manifestList, tblMle.getDescription(), osName, osVersion, oemName);

                                return s;
	}

                        /**
                         * 
                         * @param mleData
                         * @return 
                         */
	private TblMle getTblMle(MleData mleData, String mleUuid, String osOemUuid) {
		TblMle tblMle = new TblMle();

		tblMle.setMLEType(mleData.getMleType());
		tblMle.setName(mleData.getName());
		tblMle.setVersion(mleData.getVersion());
		tblMle.setAttestationType(mleData.getAttestationType());
		tblMle.setDescription(mleData.getDescription());

		tblMle.setRequiredManifestList(getRequiredManifestList(mleData
				.getManifestList()));

                // Feature: 917: Need to add the MLE, OS and OEM UUIDs
                if (mleUuid != null && !mleUuid.isEmpty())
                    tblMle.setUuid_hex(mleUuid);
                else
                    tblMle.setUuid_hex(new com.intel.dcsg.cpg.io.UUID().toString());
                
		if (mleData.getMleType().equals("VMM")) {
			tblMle.setOsId(getTblOs(mleData.getOsName(), mleData.getOsVersion()));
                        tblMle.setOs_uuid_hex(osOemUuid);
		} else if (mleData.getMleType().equals("BIOS")) {
			tblMle.setOemId(getTblOem(mleData.getOemName()));
                        tblMle.setOem_uuid_hex(osOemUuid);
		}

		return tblMle;
	}

                        /**
                         * 
                         * @param mleManifests
                         * @return 
                         */
	private List<String> manifestNames(List<ManifestData> mleManifests) {
		ArrayList<String> names = new ArrayList<>();
		for( ManifestData manifestData : mleManifests ) {
			names.add( manifestData.getName().trim() );
		}
		return names;
	}
        
                        /**
                         * 
                         * @param mleManifests
                         * @return 
                         */
	private String getRequiredManifestList(List<ManifestData> mleManifests) {
		String manifestList = mleManifests == null ? "" : StringUtils.join(manifestNames(mleManifests), ",");
		log.debug("Required Manifest list: " + manifestList);
		return manifestList;
	}

                        /**
                         * 
                         * @param label
                         * @param input
                         * @return 
                         */
	private String validateNull(String label, String input) {
		if (input == null || input.isEmpty()) {
			// log.debug(String.format("Required input parameter '%s' is null or missing.", label));
                        log.debug("Required input parameter {} is null or missing.", label);
			throw new ASException(ErrorCode.WS_MLE_DATA_MISSING, label);
		}
		return input;
	}

                        /**
                         * 
                         */
	private void addPcrManifest(TblMle tblMle, List<ManifestData> mleManifests, EntityManager em, String uuid) {
		
		tblMle.setTblPcrManifestCollection(new ArrayList<TblPcrManifest>());

		if (mleManifests != null) {

			for (ManifestData manifestData : mleManifests) {
                try {
                log.debug("add pcr manifest name: {}", manifestData.getName());
                log.debug("add pcr manifest value: '{}'", manifestData.getValue());
                
				TblPcrManifest pcrManifest = new TblPcrManifest();
                                if (uuid != null && !uuid.isEmpty())
                                    pcrManifest.setUuid_hex(uuid);
                                else
                                    pcrManifest.setUuid_hex(new UUID().toString());
				pcrManifest.setName(manifestData.getName());
                                                                                                // Bug: 375. Need to ensure we are accepting only valid hex strings.
                                                                                                validateWhitelistValue(manifestData.getName(), manifestData.getValue()); // throws exception if invalid
                                                                                                        pcrManifest.setValue(manifestData.getValue());
                                                                                                // @since 1.1 we are relying on the audit log for "created on", "created by", etc. type information
                                                                                                /*
				pcrManifest.setCreatedOn(today);
				pcrManifest.setCreatedBy(getLoggedInUser());
				pcrManifest.setUpdatedBy(getLoggedInUser());
				pcrManifest.setUpdatedOn(today);
                                                                                                */
				pcrManifest.setMleId(tblMle);
                                pcrManifest.setMle_uuid_hex(tblMle.getUuid_hex());
                                if (em == null)
                                    pcrManifestJpaController.create(pcrManifest);
                                else
                                    pcrManifestJpaController.create_v2(pcrManifest, em);
                }
                catch(Exception e) {
                    // log.error("Cannot add PCR "+manifestData.getName()+" to MLE: "+e.toString());
                    log.error("Cannot add PCR {} to MLE: {}." , manifestData.getName(), e.toString());
                    // XXX should we continue the loop to the next PCR, trying to add as many as we can?  or should we be checking all the PCR values early, and if any one of them is bad don't add ANY pcr's??
                }
			}
		}

	}

        /**
         * 
         */
	private void setTblMle(TblMle tblMle, MleData mleData) {
		// tblMle.setMLEType(mleData.getMleType().toString());
		// tblMle.setAttestationType(mleData.getAttestationType().toString());
		tblMle.setDescription(mleData.getDescription());
		tblMle.setRequiredManifestList(getRequiredManifestList(mleData
				.getManifestList()));
	}

                        /**
                         * 
                         * @param tblMle
                         * @param mleData
                         * @throws NonexistentEntityException
                         * @throws ASDataException 
                         */
	private void updatePcrManifest(TblMle tblMle, MleData mleData) throws NonexistentEntityException, ASDataException {
		HashMap<String, String> newPCRMap = getPcrMap(mleData);

		if (tblMle.getTblPcrManifestCollection() != null) { // this can be null for MODULE Manifest

			for (TblPcrManifest pcrManifest : tblMle.getTblPcrManifestCollection()) {
				if (newPCRMap.containsKey(pcrManifest.getName())) {
					log.debug(String.format("Updating Pcr manifest value for mle %s  version %s pcr name %s",
                                                                                                                                        pcrManifest.getMleId().getName(), pcrManifest.getMleId().getVersion(),  pcrManifest.getName()));
					// Bug 375
                                                                                                                        validateWhitelistValue(pcrManifest.getName(), newPCRMap.get(pcrManifest.getName())); // throws exception if invalid
                                                                                                                                pcrManifest.setValue(newPCRMap.get(pcrManifest.getName()));
                                                                                                                        
                                                                                                                         // @since 1.1 we are relying on the audit log for "created on", "created by", etc. type information
					// pcrManifest.setUpdatedBy(getLoggedInUser());
					// pcrManifest.setUpdatedOn(today);
					pcrManifestJpaController.edit(pcrManifest);
					newPCRMap.remove(pcrManifest.getName());
				} else {
					log.debug(String.format("Deleting Pcr manifest value for mle %s  version %s pcr name %s",
						pcrManifest.getMleId().getName(), pcrManifest.getMleId().getVersion(),  pcrManifest.getName()));
					pcrManifestJpaController.destroy(pcrManifest.getId());
				}
			}
                        
			for (String pcrName : newPCRMap.keySet()) {

				TblPcrManifest pcrManifest = new TblPcrManifest();
				pcrManifest.setName(pcrName);
                                                                                                // Bug 375
                                                                                                validateWhitelistValue(pcrName, newPCRMap.get(pcrName)); // throws exception if invalid
                                                                                                        pcrManifest.setValue(newPCRMap.get(pcrName));
                                                                                                // @since 1.1 we are relying on the audit log for "created on", "created by", etc. type information
                                                                                                /*
				pcrManifest.setCreatedOn(today);
				pcrManifest.setCreatedBy(getLoggedInUser());
				pcrManifest.setUpdatedBy(getLoggedInUser());
				pcrManifest.setUpdatedOn(today);
                                                                                                */
				pcrManifest.setMleId(tblMle);
                                // Since we are creating a new PCR manifeest, we need to generate a UUID
                                pcrManifest.setUuid_hex(new UUID().toString());
                                pcrManifest.setMle_uuid_hex(tblMle.getUuid_hex());

				log.debug(String.format("Creating Pcr manifest value for mle %s  version %s pcr name %s",
					pcrManifest.getMleId().getName(), pcrManifest.getMleId().getVersion(), pcrManifest.getName()));

				pcrManifestJpaController.create(pcrManifest);
			}
		}

	}

                        /**
                         * 
                         * @param mleData
                         * @return 
                         */
	private HashMap<String, String> getPcrMap(MleData mleData) {
		HashMap<String, String> pcrMap = new HashMap<>();

		if (mleData.getManifestList() != null) {
			for (ManifestData manifestData : mleData.getManifestList()) {
				pcrMap.put(manifestData.getName(), manifestData.getValue());
			}
		}

		return pcrMap;
	}

                        /**
                         * 
                         * @param osName
                         * @param osVersion
                         * @return 
                         */
	private TblOs getTblOs(String osName, String osVersion) {
		TblOs tblOs = new TblOsJpaController(getEntityManagerFactory())
				.findTblOsByNameVersion(osName, osVersion);

		if (tblOs == null)
			throw new ASException(ErrorCode.WS_OS_DOES_NOT_EXIST, osName, osVersion);

		return tblOs;
	}

                        /**
                         * 
                         * @param oemName
                         * @return 
                         */
	private TblOem getTblOem(String oemName) {
		TblOem tblOem = new TblOemJpaController(getEntityManagerFactory())
				.findTblOemByName(oemName);

		if (tblOem == null)
			throw new ASException(ErrorCode.WS_OEM_DOES_NOT_EXIST, oemName);

		return tblOem;
	}

                        /**
                         * 
                         * @param osName
                         * @param osVersion
                         * @param oemName 
                         */
	private void validateMleExtraAttributes(String osName, String osVersion, String oemName) {
		if (StringUtils.isNotBlank(oemName)) {
			if ((StringUtils.isNotBlank(osName) || StringUtils.isNotBlank(osVersion)))
				throw new ASException(ErrorCode.WS_OEM_OS_DATA_CANNOT_COEXIST);
		} else if (StringUtils.isBlank(osName) || StringUtils.isBlank(osVersion)) {
			throw new ASException(ErrorCode.WS_MLE_DATA_MISSING, "OEM/OS");
		}

	}

	public String addPCRWhiteList(PCRWhiteList pcrData) {
            return addPCRWhiteList(pcrData, null, null, null);
        }        
                        /**
                         * Added By: Sudhir on June 20, 2012
                         * 
                         * Processes the add request for a new PCR white list for the specified MLE.
                         * 
                         * @param pcrData: White list data sent by the user
                         * @return : true if the call is successful or else exception.
                         */
	public String addPCRWhiteList(PCRWhiteList pcrData, EntityManager em, String uuid, String mleUuid) {
                                TblMle tblMle;
                                TblPcrManifest tblPcr;
                                try {
                                    if (mleUuid != null && !mleUuid.isEmpty()) {
                                        tblMle = mleJpaController.findTblMleByUUID(mleUuid);
                                    } else {
                                        try {
                                            // First check if the entry exists in the MLE table.
                                            tblMle = getMleDetails(pcrData.getMleName(),
                                                            pcrData.getMleVersion(), pcrData.getOsName(),
                                                            pcrData.getOsVersion(), pcrData.getOemName());
                                        } catch (NoResultException nre){
                                            throw new ASException(ErrorCode.WS_MLE_DOES_NOT_EXIST, pcrData.getMleName(), pcrData.getMleVersion());
                                        }
                                    }
                                    
                                    if (tblMle == null) {
                                        log.error("MLE specified is not found in the DB");
                                        throw new ASException(ErrorCode.WS_MLE_RETRIEVAL_ERROR, this.getClass().getSimpleName());
                                    }
                                    
                                    // this code was checking for NoResultException but this exception is not thrown... the function findByMleIdName() called inside getPCRWhiteListDetails returns null if the record was not found.
                    //                try {
                                        // Now we need to check if PCR is already configured. If yes, then
                                        // we ned to ask the user to use the Update option instead of create
                                        tblPcr = getPCRWhiteListDetails(tblMle.getId(), pcrData.getPcrName());
                                        if (tblPcr != null) {
                                            throw new ASException(ErrorCode.WS_PCR_WHITELIST_ALREADY_EXISTS, pcrData.getPcrName());
                                        }
                    //                } catch (NoResultException nre) {
                                        // we need to ignore this exception as this is expected. We should not find any new rows already
                                        // existing in the database.
                    //                }

                                    // In order to reuse the addPCRManifest function, we need to create a list and
                                    // add a single entry into it using the manifest data that we got.
                                    List<ManifestData> pcrWhiteList = new ArrayList<>();
                                    pcrWhiteList.add(new ManifestData(pcrData.getPcrName(), pcrData.getPcrDigest()));

                                    // Now add the pcr to the database.
                                    addPcrManifest(tblMle, pcrWhiteList, em, uuid);

                                } catch (ASException ase) {
                                    throw ase;
                                } catch (Exception e) {
                    //                throw new ASException(ErrorCode.SYSTEM_ERROR, "Exception while adding PCR white list data. " + e.getMessage(), e);
                                    // throw new ASException(e);
                                    log.error("Error during PCR whitelist creation.", e);
                                    throw new ASException(ErrorCode.WS_PCR_WHITELIST_CREATE_ERROR, e.getClass().getSimpleName());            
                                }
                                return "true";
	}
        
        
                        /**
                         * Added By: Sudhir on June 20, 2012
                         * 
                         * Retrieves the details of the PCR manifest entry if exists.
                         * 
                         * @param mle_id : Identity of the MLE
                         * @param pcrName : Name of the PCR
                         * @return : Data row containing the PCR manifest details.
                         */
	private TblPcrManifest getPCRWhiteListDetails(Integer mle_id, String pcrName) {
                                TblPcrManifest tblPcr;
                                validateNull("pcrName", pcrName);
                                tblPcr = pcrManifestJpaController.findByMleIdName(mle_id, pcrName);
                                return tblPcr;
	}

        
        	public String updatePCRWhiteList(PCRWhiteList pcrData) {
                    return updatePCRWhiteList(pcrData, null, null);
                }
        
                        /**
                         * Added By: Sudhir on June 20, 2012
                         * 
                         * Processes the update request for an existing PCR white list for the specified MLE.
                         * 
                         * @param pcrData: White list data sent by the user
                         * @return : true if the call is successful or else exception.
                         */
	public String updatePCRWhiteList(PCRWhiteList pcrData, EntityManager em, String uuid) {
                                TblMle tblMle;
                                TblPcrManifest tblPcr; 

                                try {

                                    if (uuid != null && !uuid.isEmpty()) {
                                        tblPcr = pcrManifestJpaController.findTblPcrManifestByUuid(uuid);
                                    } else {
                                        try {
                                            // First check if the entry exists in the MLE table.
                                            tblMle = getMleDetails(pcrData.getMleName(),
                                                            pcrData.getMleVersion(), pcrData.getOsName(),
                                                            pcrData.getOsVersion(), pcrData.getOemName());
                                        } catch (NoResultException nre){
                                            throw new ASException(ErrorCode.WS_MLE_DOES_NOT_EXIST, pcrData.getMleName(), pcrData.getMleVersion());
                                        }
                                        
                                        tblPcr = getPCRWhiteListDetails(tblMle.getId(), pcrData.getPcrName());
                                    }
                                                                           
                                    if (tblPcr == null) {
                                        throw new ASException(ErrorCode.WS_PCR_WHITELIST_DOES_NOT_EXIST, pcrData.getPcrName());
                                    }

                                    // Now update the pcr in the database.
                                    validateWhitelistValue(pcrData.getPcrName(), pcrData.getPcrDigest()); // throws exception if invalid
                                        tblPcr.setValue(pcrData.getPcrDigest());
                                    // @since 1.1 we are relying on the audit log for "created on", "created by", etc. type information
                                    /*
                                    tblPcr.setUpdatedBy(getLoggedInUser());
                                    tblPcr.setUpdatedOn(new Date(System.currentTimeMillis()));
                                    */
                                    if (em == null) {
                                        pcrManifestJpaController.edit(tblPcr);
                                    } else {
                                        pcrManifestJpaController.edit_v2(tblPcr, em);
                                    }

                                } catch (ASException ase) {
                                    throw ase;
                                } catch (Exception e) {
                    //                throw new ASException(ErrorCode.SYSTEM_ERROR, "Exception while updating PCR white list data. " + e.getMessage(), e);
                                    // throw new ASException(e);
                                    log.error("Error during PCR whitelist update.", e);
                                    throw new ASException(ErrorCode.WS_PCR_WHITELIST_UPDATE_ERROR, e.getClass().getSimpleName());            
                                }
                                return "true";
	}
        
        
                        /**
                         * 
                         * Added By: Sudhir on June 20, 2012
                         * 
                         * Processes the delete request for an existing PCR white list for the specified MLE.
                         * 
                         * @param pcrName : Name of the PCR, which is usually the number
                         * @param mleName : Name of the associated MLE
                         * @param mleVersion : Version of the associated MLE
                         * @param osName : OS name associated with the VMM MLE
                         * @param osVersion : OS version associated with the VMM MLE
                         * @param oemName : OEM Name associated with the BIOS MLE
                         * @return 
                         */
	public String deletePCRWhiteList(String pcrName, String mleName, String mleVersion, String osName,  String osVersion, String oemName, String pcrUuid) {
                                TblPcrManifest tblPcr;
                                TblMle tblMle;
                                try {
                                    
                                    if (pcrUuid != null && !pcrUuid.isEmpty()) {
                                        tblPcr = pcrManifestJpaController.findTblPcrManifestByUuid(pcrUuid);                                        
                                    } else {
                                    
                                        try {
                                            tblMle = getMleDetails(mleName, mleVersion, osName, osVersion, oemName);
                                        } catch (NoResultException nre){
                                            // If the MLE is not configured, then return back a proper error
                                            throw new ASException(nre,ErrorCode.WS_MLE_DOES_NOT_EXIST,mleName, mleVersion);
                                        }
                                        
                                        // Now get the PCR details
                                        tblPcr = getPCRWhiteListDetails(tblMle.getId(), pcrName);

                                    }
                                                                        
                                    if (tblPcr == null)
                                        return "true";

                                    // Delete the PCR white list entry.
                                    pcrManifestJpaController.destroy(tblPcr.getId());

                                } catch (ASException ase) {
                                        throw ase;
                                } catch (Exception e) {
                    //                    throw new ASException(ErrorCode.SYSTEM_ERROR, "Exception while deleting PCR white list data. " + e.getMessage(), e);
                                    // throw new ASException(e);
                                    log.error("Error during PCR whitelist deletion.", e);
                                    throw new ASException(ErrorCode.WS_PCR_WHITELIST_DELETE_ERROR, e.getClass().getSimpleName());            
                                }                
                                return "true";
	}
        
        public String addModuleWhiteList(ModuleWhiteList moduleData) {
            return addModuleWhiteList(moduleData, null, null, null);
        }

        /**
         * Added By: Sudhir on June 21, 2012
         * 
         * Adds a new module white list into the Module Manifest Table
         * 
         * @param moduleData: Data of the white list
         * @return : "true" if everything is successful or else exception
         */
        public String addModuleWhiteList(ModuleWhiteList moduleData, EntityManager em, String uuid, String mleUuid) {
            TblMle tblMle;
            TblEventType tblEvent;
            TblPackageNamespace nsPackNS;
            //TblModuleManifest tblModule = null;
            String fullComponentName ;
            long addModule = System.currentTimeMillis();
            
            try {

                if (mleUuid != null && !mleUuid.isEmpty()) {
                    tblMle = mleJpaController.findTblMleByUUID(mleUuid);
                } else {
                    try {
                        // First check if the entry exists in the MLE table.
                        tblMle = getMleDetails(moduleData.getMleName(),
                                        moduleData.getMleVersion(), moduleData.getOsName(),
                                        moduleData.getOsVersion(), moduleData.getOemName());
                    } catch (NoResultException nre){
                        throw new ASException(nre,ErrorCode.WS_MLE_DOES_NOT_EXIST, moduleData.getMleName(), moduleData.getMleVersion());
                    }
                }
                
                if (tblMle == null) {
                    log.error("MLE specified is not found in the DB");
                    throw new ASException(ErrorCode.WS_MLE_RETRIEVAL_ERROR, this.getClass().getSimpleName());
                }
                
                long addModule1 = System.currentTimeMillis();
                log.debug("ADDMLETIME: after retrieving MLE info - {} ", (addModule1 - addModule));                
                try {
                    // Before we insert the record, we need the identity for the event name
                    tblEvent = eventTypeJpaController.findEventTypeByName(moduleData.getEventName());
                    
                } catch (NoResultException nre){
                    throw new ASException(nre,ErrorCode.WS_EVENT_TYPE_DOES_NOT_EXIST, moduleData.getEventName());
                }
                
                // this was catching NoResultException which is not thrown by findByMleNameEventName() ... it returns null if the record was not found.             
//                try {
                    // Now we need to check if Module is already configured. If yes, then
                    // we need to ask the user to use the Update option instead of create
                    validateNull("ComponentName", moduleData.getComponentName());
                    validateNull("EventName", moduleData.getEventName());
                    log.debug("addModuleWhiteList searching for module manifest with field name '"+tblEvent.getFieldName()+"' component name '"+moduleData.getComponentName()+"' event name '"+moduleData.getEventName()+"'");

                    // For Open Source hypervisors, we do not want to prefix the event type field name. So, we need to check if the event name
                    // corresponds to VMware, then we will append the event type fieldName to the component name. Otherwise we won't
                    if (moduleData.getEventName().contains("Vim25")) {
                        fullComponentName = tblEvent.getFieldName() + "." + moduleData.getComponentName();
                    } else {
                        fullComponentName = moduleData.getComponentName();
                    }
                    // fix for Bug #730 that affected postgres only because postgres does not automatically trim spaces on queries but mysql automatically trims
                    if( fullComponentName != null ) {
                        log.debug("trimming fullComponentName: " + fullComponentName);
                        fullComponentName = fullComponentName.trim(); 
                    }
                    log.debug("uploadToDB searching for module manifest with fullComponentName '" + fullComponentName + "'");

                    long addModule2 = System.currentTimeMillis();
                    log.debug("ADDMLETIME: after retrieving Event info - {}", (addModule2 - addModule1));  
                    
                    //tblModule = moduleManifestJpaController.findByMleNameEventName(tblMle.getId(), fullComponentName, moduleData.getEventName());
                    Integer componentID = moduleManifestJpaController.findByMleIdEventId(tblMle.getId(), fullComponentName, tblEvent.getId());
                    
                    if (componentID != null && componentID != 0) {
                        throw new ASException(ErrorCode.WS_MODULE_WHITELIST_ALREADY_EXISTS, moduleData.getComponentName());
                    }
//                } catch (NoResultException nre){
                    // This is expected since we are adding the module white list. So, continue.
//                }
                
                    long addModule3 = System.currentTimeMillis();
                    log.debug("ADDMLETIME: after searching for Module info - {} ", (addModule3 - addModule2));  
                    
                try {

                    // Since there will be only one entry for now, we will just hardcode it for now.
                    // TO-DO: See if we can change this.
                    // Nov-12,2013: Changed to use the function that accepts the ID instead of the name for better
                    // performance.
                    nsPackNS = packageNSJpaController.findByName("Standard_Global_NS");

                } catch (NoResultException nre){
                    throw new ASException(ErrorCode.WS_NAME_SPACE_DOES_NOT_EXIST);
                }
                                
                    long addModule4 = System.currentTimeMillis();
                    log.debug("ADDMLETIME: after searching for package info - {} ", (addModule4 - addModule3));  

                    TblModuleManifest newModuleRecord = new TblModuleManifest();
                    if (uuid != null && !uuid.isEmpty())
                        newModuleRecord.setUuid_hex(uuid);
                    else
                        newModuleRecord.setUuid_hex(new UUID().toString());
                    
                newModuleRecord.setMleId(tblMle);
                newModuleRecord.setMle_uuid_hex(tblMle.getUuid_hex());
                
                newModuleRecord.setEventID(tblEvent);
                newModuleRecord.setNameSpaceID(nsPackNS);
                log.debug("MleBO addModuleWhiteList setComponentName {}", fullComponentName);
                newModuleRecord.setComponentName(fullComponentName);
                
                // Bug 375: If the white list is not valid, then an exception would be thrown.
                validateWhitelistValue(moduleData.getComponentName(), moduleData.getDigestValue()); // throws exception if invalid
                        newModuleRecord.setDigestValue(moduleData.getDigestValue());
                
                newModuleRecord.setPackageName(moduleData.getPackageName());
                newModuleRecord.setPackageVendor(moduleData.getPackageVendor());
                newModuleRecord.setPackageVersion(moduleData.getPackageVersion());
                newModuleRecord.setUseHostSpecificDigestValue(moduleData.getUseHostSpecificDigest());
                newModuleRecord.setExtendedToPCR(moduleData.getExtendedToPCR());
                newModuleRecord.setDescription(moduleData.getDescription());
                // @since 1.1 we are relying on the audit log for "created on", "created by", etc. type information
                /*
                newModuleRecord.setCreatedBy(getLoggedInUser());
                newModuleRecord.setCreatedOn(new Date(System.currentTimeMillis()));
                */
                // Create the new white list record.
                long addModule5 = System.currentTimeMillis();
                log.debug("ADDMLETIME: before insert {} ", (addModule5 - addModule4));
                if (em == null) {
                    moduleManifestJpaController.create(newModuleRecord);
                } else {
                    log.debug("ADDMLETIME: Using the new create method of having EM.");
                    moduleManifestJpaController.create_v2(newModuleRecord, em);
                }
                log.debug("ADDMLETIME: after insert {}", (System.currentTimeMillis() - addModule5));

            } catch (ASException ase) {
                    throw ase;
            } catch (Exception e) {
//                    throw new ASException(ErrorCode.SYSTEM_ERROR, "Exception while adding Module white list data. " + e.getMessage(), e);
                // throw new ASException(e);
                log.error("Error during Module whitelist creation.", e);
                throw new ASException(ErrorCode.WS_MODULE_WHITELIST_CREATE_ERROR, e.getClass().getSimpleName());            
            }                                
            return "true";
	}

        
        public String updateModuleWhiteList(ModuleWhiteList moduleData) {
            return updateModuleWhiteList(moduleData, null, null);
        }
        
        /**
         * Added By: Sudhir on June 21, 2012
         * 
         * Updates an existing module white list in the Module Manifest Table
         * 
         * @param moduleData: Data of the white list
         * @return : "true" if everything is successful or else exception
         */
        public String updateModuleWhiteList(ModuleWhiteList moduleData, EntityManager em, String uuid) {
            TblMle tblMle;
            TblEventType tblEvent;
            TblPackageNamespace nsPackNS;
            TblModuleManifest tblModule;
            String fullComponentName;
            
            try {
                
                if (uuid != null &&  !uuid.isEmpty()) {
                    
                    tblModule = moduleManifestJpaController.findTblModuleManifestByUuid(uuid);
                    
                } else {
                    
                    try {
                        // First check if the entry exists in the MLE table.
                        tblMle = getMleDetails(moduleData.getMleName(),
                                        moduleData.getMleVersion(), moduleData.getOsName(),
                                        moduleData.getOsVersion(), moduleData.getOemName());
                    } catch (NoResultException nre){
                        throw new ASException(nre,ErrorCode.WS_MLE_DOES_NOT_EXIST, moduleData.getMleName(), moduleData.getMleVersion());
                    }

                    try {

                        // Before we insert the record, we need the identity for the event name
                        tblEvent = eventTypeJpaController.findEventTypeByName(moduleData.getEventName());

                    } catch (NoResultException nre){
                        throw new ASException(nre,ErrorCode.WS_EVENT_TYPE_DOES_NOT_EXIST, moduleData.getEventName());
                    }

                    try {
                        // Now we need to check if Module is already configured. If yes, then
                        // we need to ask the user to use the Update option instead of create
                        validateNull("ComponentName", moduleData.getComponentName());
                        validateNull("EventName", moduleData.getEventName());
                        log.debug("updateModuleWhiteList searching for module manifest with field name '"+tblEvent.getFieldName()+"' component name '"+moduleData.getComponentName()+"' event name '"+moduleData.getEventName()+"'");

                        // For Open Source hypervisors, we do not want to prefix the event type field name. So, we need to check if the event name
                        // corresponds to VMware, then we will append the event type fieldName to the component name. Otherwise we won't
                        if (moduleData.getEventName().contains("Vim25")) {
                            fullComponentName = tblEvent.getFieldName() + "." + moduleData.getComponentName();
                        } else {
                            fullComponentName = moduleData.getComponentName();
                        }
                        // fix for Bug #730 that affected postgres only because postgres does not automatically trim spaces on queries but mysql automatically trims
                        if( fullComponentName != null ) {
                            log.debug("trimming fullComponentName: " + fullComponentName);
                            fullComponentName = fullComponentName.trim(); 
                        }
                        log.debug("uploadToDB searching for module manifest with fullComponentName '" + fullComponentName + "'");

                        tblModule = moduleManifestJpaController.findByMleNameEventName(tblMle.getId(), fullComponentName, moduleData.getEventName());

                    } catch (NoResultException nre){
                        throw new ASException(nre,ErrorCode.WS_MODULE_WHITELIST_DOES_NOT_EXIST, moduleData.getComponentName());                    
                    }

                    if(! packageNSJpaController.namespaceExists("Standard_Global_NS"))
                        throw new ASException(ErrorCode.WS_NAME_SPACE_DOES_NOT_EXIST);
                }
                
                validateWhitelistValue(moduleData.getComponentName(), moduleData.getDigestValue()); // throws exception if invalid
                tblModule.setDigestValue(moduleData.getDigestValue());
                
                tblModule.setDescription(moduleData.getDescription());
                // @since 1.1 we are relying on the audit log for "created on", "created by", etc. type information
                /*
                tblModule.setUpdatedBy(getLoggedInUser());
		tblModule.setUpdatedOn(new Date(System.currentTimeMillis()));
                */
                // Create the new white list record.
                if (em == null) {
                    moduleManifestJpaController.edit(tblModule);
                } else {
                    moduleManifestJpaController.edit_v2(tblModule, em);
                }

            } catch (ASException ase) {
                    throw ase;
            } catch (Exception e) {
//                    throw new ASException(ErrorCode.SYSTEM_ERROR, "Exception while updating Module white list data. " + e.getMessage(), e);
                // throw new ASException(e);
                log.error("Error during Module whitelist update.", e);
                throw new ASException(ErrorCode.WS_MODULE_WHITELIST_UPDATE_ERROR, e.getClass().getSimpleName());            
            }                                
                
            return "true";
	}
        
        
         /**
         * Added By: Sudhir on June 21, 2012
         * 
         * Deletes an existing module white list in the Module Manifest Table
         * 
         * @param componentName : Name of the component for which we need to delete the white list
         * @param eventName : Associated event name
         * @param mleName : Name of the measured launch environment (MLE) associated with the white list.
         * @param mleVersion : Version of the MLE or Hypervisor
         * @param osName : Name of the OS running the hypervisor. OS Details need to be provided only
         * when the associated MLE is of VMM type.
         * @param osVersion : Version of the OS
         * @param oemName : OEM vendor of the hardware system. OEM Details have to be provided only 
         * when the associated MLE is of BIOS type.
         * @return : "true" if everything is successful or else exception
         */
        public String deleteModuleWhiteList(String componentName, String eventName, String mleName, String mleVersion, 
                String osName, String osVersion, String oemName, String uuid) {
            TblMle tblMle;
            TblEventType tblEvent;
            TblPackageNamespace nsPackNS;
            TblModuleManifest tblModule;
            
            try {
                
                if (uuid != null && !uuid.isEmpty()) {
                    
                    tblModule = moduleManifestJpaController.findTblModuleManifestByUuid(uuid);
                    
                } else {
                    
                    try {
                        // First check if the entry exists in the MLE table.
                        tblMle = getMleDetails(mleName, mleVersion, osName, osVersion, oemName);

                    } catch (NoResultException nre){
                        throw new ASException(nre,ErrorCode.WS_MLE_DOES_NOT_EXIST, mleName, mleVersion);
                    }

                    try {

                        // Before we insert the record, we need the identity for the event name
                        tblEvent = eventTypeJpaController.findEventTypeByName(eventName);

                    } catch (NoResultException nre){
                        throw new ASException(nre, ErrorCode.WS_EVENT_TYPE_DOES_NOT_EXIST, eventName);
                    }

                    try {
                        // Now we need to check if Module is already configured. If yes, then
                        // we need to ask the user to use the Update option instead of create
                        validateNull("ComponentName", componentName);
                        validateNull("EventName", eventName);
                        String fullComponentName = tblEvent.getFieldName() + "." + componentName;

                        // fix for Bug #730 that affected postgres only because postgres does not automatically trim spaces on queries but mysql automatically trims
                        if( fullComponentName != null ) {
                            log.debug("trimming fullComponentName: " + fullComponentName);
                            fullComponentName = fullComponentName.trim(); 
                        }
                        log.debug("uploadToDB searching for module manifest with fullComponentName '" + fullComponentName + "'");

                        log.debug("deleteModuleWhiteList searching for module manifest with field name '"+tblEvent.getFieldName()+"' component name '"+componentName+"' event name '"+eventName+"'");
                        tblModule = moduleManifestJpaController.findByMleNameEventName(tblMle.getId(), 
                                tblEvent.getFieldName() + "." + componentName, eventName);

                    } catch (NoResultException nre){
                        // If the module manifest that we are trying to delete does not exist, it is ok.
                        // Just return back success
                        return "true";
                    }
                }
                
                if (tblModule != null) 
                    moduleManifestJpaController.destroy(tblModule.getId());
                
            } catch (ASException ase) {
                    throw ase;
            } catch (Exception e) {
//                    throw new ASException(ErrorCode.SYSTEM_ERROR, "Exception while deleting Module white list data. " + e.getMessage(), e);
                // throw new ASException(e);
                log.error("Error during Module whitelist deletion.", e);
                throw new ASException(ErrorCode.WS_MODULE_WHITELIST_DELETE_ERROR, e.getClass().getSimpleName());            
            }                                
                
            return "true";
	}
        
        
        /**
         * Added By: Sudhir on June 21, 2012
         * 
         * Retrieves the list of all the module white lists for the specified MLE.
         * 
         * @param mleName : Name of the measured launch environment (MLE) associated with the white list.
         * @param mleVersion : Version of the MLE or Hypervisor
         * @param osName : Name of the OS running the hypervisor. OS Details need to be provided only
         * when the associated MLE is of VMM type.
         * @param osVersion : Version of the OS
         * @param oemName : OEM vendor of the hardware system. OEM Details have to be provided only 
         * when the associated MLE is of BIOS type.
         * @return : List of all the module white lists.
         */
        public List<ModuleWhiteList> getModuleWhiteList(String mleName, String mleVersion, 
                String osName, String osVersion, String oemName) {
            TblMle tblMle;
            List<ModuleWhiteList> modManifestList = new ArrayList<> ();
            List<TblModuleManifest> tblModList;
            try {
                
                try {
                    // First check if the entry exists in the MLE table.
                    tblMle = getMleDetails(mleName, mleVersion, osName, osVersion, oemName);

                } catch (NoResultException nre){
                    throw new ASException(nre,ErrorCode.WS_MLE_DOES_NOT_EXIST,  mleName, mleVersion);
                }
                                  
                try {
                    
                    tblModList = moduleManifestJpaController.findByMleId(tblMle.getId());
                    for (int i = 0; i < tblModList.size(); i++){
                        ModuleWhiteList modObj = new ModuleWhiteList();
                        modObj.setMleName(tblModList.get(i).getMleId().getName());
                        modObj.setMleVersion(tblModList.get(i).getMleId().getVersion());
                        if (oemName == null || oemName.isEmpty())
                        {
                            modObj.setOsName(tblModList.get(i).getMleId().getOsId().getName());
                            modObj.setOsVersion(tblModList.get(i).getMleId().getOsId().getVersion());
                            modObj.setOemName("");
                        }
                        else
                        {
                            modObj.setOsName("");
                            modObj.setOsVersion("");
                            modObj.setOemName(tblModList.get(i).getMleId().getOemId().getName()); 
                        }
                        modObj.setEventName(tblModList.get(i).getEventID().getName());
                        modObj.setComponentName(tblModList.get(i).getComponentName());
                        modObj.setDigestValue(tblModList.get(i).getDigestValue());
                        modObj.setExtendedToPCR(tblModList.get(i).getExtendedToPCR());
                        modObj.setPackageName(tblModList.get(i).getPackageName());
                        modObj.setPackageVendor(tblModList.get(i).getPackageVendor());
                        modObj.setPackageVersion(tblModList.get(i).getPackageVersion());
                        modObj.setUseHostSpecificDigest(tblModList.get(i).getUseHostSpecificDigestValue()); 
                        modObj.setDescription(tblModList.get(i).getDescription());
                        
                        modManifestList.add(modObj);
                    }
                    
                } catch (NoResultException nre){
                    // If the module manifest that we are trying to delete does not exist, it is ok.
                    // Just return back success
                    return modManifestList;
                }
                
            } catch (ASException ase) {
                    throw ase;
            } catch (Exception e) {
//                    throw new ASException(ErrorCode.SYSTEM_ERROR, "Exception while retrieving Module white list data. " + e.getMessage(), e);
                // throw new ASException(e);
                log.error("Error during retrieval of Module whitelists for MLE.", e);
                throw new ASException(ErrorCode.WS_MODULE_WHITELIST_RETRIEVAL_ERROR, e.getClass().getSimpleName());            
            }                                
                
            return modManifestList;
	}
        
        /**
         * Creates a new mapping entry in the DB between the MLE and the host that was used for whitelisiting.
         * 
         * @param mleSourceObj : Object containing the details of the host and the MLE.
         * @return True or False
         */
        public String addMleSource(MleSource mleSourceObj, String uuid, String mleUuid) {
            TblMle tblMle;
            MleData mleData ;
            try {

                if (mleUuid != null && !mleUuid.isEmpty()) {
                    tblMle = mleJpaController.findTblMleByUUID(mleUuid);
                } else {
                    try {
                        if (mleSourceObj == null || mleSourceObj.getMleData() == null) {
                            log.error("Required input parameters is not specified");
                            throw new ASException(ErrorCode.WS_MLE_HOST_MAP_CREATE_ERROR, this.getClass().getSimpleName());
                        }
                        mleData = mleSourceObj.getMleData();
                        // Verify if the MLE exists in the system.
                        tblMle = getMleDetails(mleData.getName(), mleData.getVersion(), mleData.getOsName(),
                                        mleData.getOsVersion(), mleData.getOemName());
                    } catch (NoResultException nre){
                        throw new ASException(ErrorCode.WS_MLE_RETRIEVAL_ERROR, nre.getClass().getSimpleName());
                    }
                }
                  
                if (tblMle == null) {
                    log.error("MLE specified is not found in the DB");
                    throw new ASException(ErrorCode.WS_MLE_RETRIEVAL_ERROR, this.getClass().getSimpleName());
                }
                
                MwMleSourceJpaController mleSourceJpaController = new MwMleSourceJpaController(getEntityManagerFactory());
                
                // Let us check if there is a mapping entry already for this MLE. If it does, then we need to return
                // back appropriate error.
                MwMleSource mleSourceCurrentObj = mleSourceJpaController.findByMleId(tblMle.getId());
                
                if (mleSourceCurrentObj != null) {
                    log.error("White List host is already mapped to the MLE - " + tblMle.getName());
                    throw new ASException(ErrorCode.WS_MLE_HOST_MAP_CREATE_ERROR, this.getClass().getSimpleName());
                }
                
                // Else create a new entry in the DB.
                MwMleSource mleSourceData = new MwMleSource();
                if (uuid != null && !uuid.isEmpty())
                    mleSourceData.setUuid_hex(uuid);
                else
                    mleSourceData.setUuid_hex(new UUID().toString());
                mleSourceData.setMleId(tblMle);
                mleSourceData.setMle_uuid_hex(tblMle.getUuid_hex());
                mleSourceData.setHostName(mleSourceObj.getHostName());        
                        
                mleSourceJpaController.create(mleSourceData);
                
            } catch (ASException ase) {
                    throw ase;
            } catch (Exception e) {
                // throw new ASException(e);
                log.error("Error during configuration of host used for creating white lists.", e);
                throw new ASException(ErrorCode.WS_MLE_HOST_MAP_CREATE_ERROR, e.getClass().getSimpleName());            
            }                                
            return "true";
	}
        
        
        /**
         * Updates an existing MLE with the name of the white list host that was used to modify the white list values.
         * @param mleSourceObj
         * @return 
         */
        public String updateMleSource(MleSource mleSourceObj, String mleUuid) {
            TblMle tblMle;
            MleData mleData ;
            MwMleSource mwMleSource;
            try {

                MwMleSourceJpaController mleSourceJpaController = new MwMleSourceJpaController(getEntityManagerFactory());
                
                if (mleUuid != null && !mleUuid.isEmpty()) {
                    mwMleSource = mleSourceJpaController.findByMleUuid(mleUuid);
                } else {
                    try {
                        if (mleSourceObj == null || mleSourceObj.getMleData() == null) {
                            log.error("Required input parameters is not specified");
                            throw new ASException(ErrorCode.WS_MLE_HOST_MAP_UPDATE_ERROR, this.getClass().getSimpleName());
                        }
                        
                        mleData = mleSourceObj.getMleData();
                        // Verify if the MLE exists in the system.
                        tblMle = getMleDetails(mleData.getName(), mleData.getVersion(), mleData.getOsName(),
                                        mleData.getOsVersion(), mleData.getOemName());
                    } catch (NoResultException nre){
                        throw new ASException(ErrorCode.WS_MLE_RETRIEVAL_ERROR, nre.getClass().getSimpleName());
                    }
                    
                    // Now retrieve the MleSource details
                    mwMleSource = mleSourceJpaController.findByMleId(tblMle.getId());
                }
                                
                // If the mapping does not exist already in the db, then we need to return back error.
                if (mwMleSource == null) {
                    throw new ASException(ErrorCode.WS_MLE_HOST_MAP_UPDATE_ERROR, this.getClass().getSimpleName());
                }
                
                mwMleSource.setHostName(mleSourceObj.getHostName());        
                mleSourceJpaController.edit(mwMleSource);
                
            } catch (ASException ase) {
                    throw ase;
            } catch (Exception e) {
                // throw new ASException(e);
                log.error("Error during update of the configuration of host used for creating white lists.", e);
                throw new ASException(ErrorCode.WS_MLE_HOST_MAP_UPDATE_ERROR, e.getClass().getSimpleName());            
            }                                
            return "true";
	}

        
        /**
         * Deletes an existing mapping between the MLE and the WhiteList host that was used during the creation of MLE.
         * This method is called during the deletion of MLEs.
         * 
         * @param mleName
         * @param mleVersion
         * @param osName
         * @param osVersion
         * @param oemName
         * @return 
         */
        public String deleteMleSource(String mleName, String mleVersion, String osName, String osVersion, String oemName, String mleUuid) {
            TblMle tblMle;
            MwMleSource mwMleSource;
            try {
                 MwMleSourceJpaController mleSourceJpaController = new MwMleSourceJpaController(getEntityManagerFactory());
                 
                if (mleUuid != null && !mleUuid.isEmpty()) {
                    mwMleSource = mleSourceJpaController.findByMleUuid(mleUuid);
                } else {                
                    try {
                        // First check if the entry exists in the MLE table.
                        tblMle = getMleDetails(mleName, mleVersion, osName, osVersion, oemName);

                    } catch (NoResultException nre){
                        throw new ASException(nre,ErrorCode.WS_MLE_DOES_NOT_EXIST, mleName, mleVersion);
                    }
                    
                    // Now retrieve the MwMleSource table entry
                    mwMleSource = mleSourceJpaController.findByMleId(tblMle.getId());
                }
                
                // If the mapping does not exist, it is ok. We don't need to worry. Actually for MLES
                // configured manully, this entry does not exist.
                if  (mwMleSource != null)                                
                    mleSourceJpaController.destroy(mwMleSource.getId());
                
            } catch (ASException ase) {
                    throw ase;
            } catch (Exception e) {
                // throw new ASException(e);
                log.error("Error during deletion of the configuration of host used for creating white lists.", e);
                throw new ASException(ErrorCode.WS_MLE_HOST_MAP_DELETE_ERROR, e.getClass().getSimpleName());            
            }                                      
            return "true";
	}

        
        /**
         * Retrieves the host name that was used to white list the MLE specified.
         * 
         * @param mleName
         * @param mleVersion
         * @param osName
         * @param osVersion
         * @param oemName
         * @return 
         */
        public String getMleSource(String mleName, String mleVersion, String osName, String osVersion, String oemName) {
            TblMle tblMle;
            String hostName ;
            try {
                
                try {
                    // First check if the entry exists in the MLE table.
                    tblMle = getMleDetails(mleName, mleVersion, osName, osVersion, oemName);

                } catch (NoResultException nre){
                    throw new ASException(nre,ErrorCode.WS_MLE_DOES_NOT_EXIST, mleName, mleVersion);
                }
                                
                MwMleSourceJpaController mleSourceJpaController = new MwMleSourceJpaController(getEntityManagerFactory());
                MwMleSource mwMleSource = mleSourceJpaController.findByMleId(tblMle.getId());
                
                // Now check if the data exists in the MLE Source table. If there is no corresponding entry, then we know that
                // the MLE was configured manually. 
                if (mwMleSource == null) {
                    hostName = "Manually configured white list";
                }
                else {
                    hostName = mwMleSource.getHostName();
                }
                
                return hostName;
                
            } catch (ASException ase) {
                    throw ase;
            } catch (Exception e) {
                // throw new ASException(e);
                log.error("Error during retrieval of host information used for creating white lists.", e);
                throw new ASException(ErrorCode.WS_MLE_HOST_MAP_RETRIEVAL_ERROR, e.getClass().getSimpleName());            
            }                                                
	}
        
}
