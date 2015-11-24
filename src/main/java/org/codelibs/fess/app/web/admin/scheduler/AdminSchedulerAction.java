/*
 * Copyright 2012-2015 CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.codelibs.fess.app.web.admin.scheduler;

import javax.annotation.Resource;

import org.codelibs.fess.Constants;
import org.codelibs.fess.app.pager.ScheduledJobPager;
import org.codelibs.fess.app.service.ScheduledJobService;
import org.codelibs.fess.app.web.CrudMode;
import org.codelibs.fess.app.web.admin.boostdoc.SearchForm;
import org.codelibs.fess.app.web.base.FessAdminAction;
import org.codelibs.fess.es.config.exentity.ScheduledJob;
import org.codelibs.fess.helper.JobHelper;
import org.codelibs.fess.helper.SystemHelper;
import org.codelibs.fess.job.JobExecutor;
import org.codelibs.fess.util.ComponentUtil;
import org.dbflute.optional.OptionalEntity;
import org.dbflute.optional.OptionalThing;
import org.lastaflute.web.Execute;
import org.lastaflute.web.response.HtmlResponse;
import org.lastaflute.web.response.render.RenderData;
import org.lastaflute.web.ruts.process.ActionRuntime;
import org.lastaflute.web.util.LaRequestUtil;

/**
 * @author shinsuke
 * @author Keiichi Watanabe
 */
public class AdminSchedulerAction extends FessAdminAction {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    @Resource
    private ScheduledJobService scheduledJobService;
    @Resource
    private ScheduledJobPager scheduledJobPager;
    @Resource
    private SystemHelper systemHelper;
    @Resource
    protected JobHelper jobHelper;

    // ===================================================================================
    //                                                                               Hook
    //                                                                              ======
    @Override
    protected void setupHtmlData(final ActionRuntime runtime) {
        super.setupHtmlData(runtime);
        runtime.registerData("helpLink", systemHelper.getHelpLink(fessConfig.getOnlineHelpNameScheduler()));
    }

    // ===================================================================================
    //                                                                      Search Execute
    //                                                                      ==============
    @Execute
    public HtmlResponse index(final SearchForm form) {
        return asListHtml();
    }

    @Execute
    public HtmlResponse list(final OptionalThing<Integer> pageNumber, final SearchForm form) {
        pageNumber.ifPresent(num -> {
            scheduledJobPager.setCurrentPageNumber(pageNumber.get());
        }).orElse(() -> {
            scheduledJobPager.setCurrentPageNumber(0);
        });
        return asHtml(path_AdminScheduler_AdminSchedulerJsp).renderWith(data -> {
            searchPaging(data, form);
        });
    }

    @Execute
    public HtmlResponse search(final SearchForm form) {
        copyBeanToBean(form, scheduledJobPager, op -> op.exclude(Constants.PAGER_CONVERSION_RULE));
        return asHtml(path_AdminScheduler_AdminSchedulerJsp).renderWith(data -> {
            searchPaging(data, form);
        });
    }

    @Execute
    public HtmlResponse reset(final SearchForm form) {
        scheduledJobPager.clear();
        return asHtml(path_AdminScheduler_AdminSchedulerJsp).renderWith(data -> {
            searchPaging(data, form);
        });
    }

    protected void searchPaging(final RenderData data, final SearchForm form) {
        data.register("scheduledJobItems", scheduledJobService.getScheduledJobList(scheduledJobPager)); // page navi

        // restore from pager
        copyBeanToBean(scheduledJobPager, form, op -> op.include("id"));
    }

    // ===================================================================================
    //                                                                        Edit Execute
    //                                                                        ============
    // -----------------------------------------------------
    //                                            Entry Page
    //                                            ----------

    @Execute
    public HtmlResponse createnewjob(final String type, final String id, final String name) {
        saveToken();
        return asHtml(path_AdminScheduler_AdminSchedulerEditJsp).useForm(
                CreateForm.class,
                op -> {
                    op.setup(scheduledJobForm -> {
                        scheduledJobForm.initialize();
                        scheduledJobForm.crudMode = CrudMode.CREATE;
                        scheduledJobForm.jobLogging = Constants.ON;
                        scheduledJobForm.crawler = Constants.ON;
                        scheduledJobForm.available = Constants.ON;
                        scheduledJobForm.name =
                                ComponentUtil.getMessageManager().getMessage(LaRequestUtil.getRequest().getLocale(),
                                        "labels." + type + "_job_title", name);
                        String[] ids = new String[] { "", "", "" };
                        if (Constants.WEB_CRAWLER_TYPE.equals(type)) {
                            ids[0] = "\"" + id + "\"";
                        } else if (Constants.FILE_CRAWLER_TYPE.equals(type)) {
                            ids[1] = "\"" + id + "\"";
                        } else if (Constants.DATA_CRAWLER_TYPE.equals(type)) {
                            ids[2] = "\"" + id + "\"";
                        }
                        scheduledJobForm.scriptData =
                                ComponentUtil.getMessageManager().getMessage(LaRequestUtil.getRequest().getLocale(),
                                        "labels.scheduledjob_script_template", ids[0], ids[1], ids[2]);
                    });
                });
    }

    @Execute
    public HtmlResponse createnew() {
        saveToken();
        return asHtml(path_AdminScheduler_AdminSchedulerEditJsp).useForm(CreateForm.class, op -> {
            op.setup(form -> {
                form.initialize();
                form.crudMode = CrudMode.CREATE;
            });
        });
    }

    @Execute
    public HtmlResponse edit(final EditForm form) {
        validate(form, messages -> {}, () -> asListHtml());
        final String id = form.id;
        scheduledJobService.getScheduledJob(id).ifPresent(entity -> {
            loadScheduledJob(form, entity);
        }).orElse(() -> {
            throwValidationError(messages -> messages.addErrorsCrudCouldNotFindCrudTable(GLOBAL, id), () -> asListHtml());
        });
        saveToken();
        if (form.crudMode.intValue() == CrudMode.EDIT) {
            // back
            form.crudMode = CrudMode.DETAILS;
            return asDetailsHtml();
        } else {
            form.crudMode = CrudMode.EDIT;
            return asEditHtml();
        }
    }

    // -----------------------------------------------------
    //                                               Details
    //                                               -------
    @Execute
    public HtmlResponse details(final int crudMode, final String id) {
        verifyCrudMode(crudMode, CrudMode.DETAILS);
        saveToken();
        return asHtml(path_AdminScheduler_AdminSchedulerDetailsJsp).useForm(EditForm.class, op -> {
            op.setup(form -> {
                scheduledJobService.getScheduledJob(id).ifPresent(entity -> {
                    loadScheduledJob(form, entity);
                    form.crudMode = crudMode;
                    LaRequestUtil.getOptionalRequest().ifPresent(request -> {
                        request.setAttribute("running", entity.isRunning());
                    });
                }).orElse(() -> {
                    throwValidationError(messages -> messages.addErrorsCrudCouldNotFindCrudTable(GLOBAL, id), () -> asListHtml());
                });
            });
        });
    }

    // -----------------------------------------------------
    //                                         Actually Crud
    //                                         -------------
    @Execute
    public HtmlResponse create(final CreateForm form) {
        verifyCrudMode(form.crudMode, CrudMode.CREATE);
        validate(form, messages -> {}, () -> asEditHtml());
        verifyToken(() -> asEditHtml());
        getScheduledJob(form).ifPresent(entity -> {
            scheduledJobService.store(entity);
            saveInfo(messages -> messages.addSuccessCrudCreateCrudTable(GLOBAL));
        }).orElse(() -> {
            throwValidationError(messages -> messages.addErrorsCrudFailedToCreateCrudTable(GLOBAL), () -> asEditHtml());
        });
        return redirect(getClass());
    }

    @Execute
    public HtmlResponse update(final EditForm form) {
        verifyCrudMode(form.crudMode, CrudMode.EDIT);
        validate(form, messages -> {}, () -> asEditHtml());
        verifyToken(() -> asEditHtml());
        getScheduledJob(form).ifPresent(entity -> {
            scheduledJobService.store(entity);
            saveInfo(messages -> messages.addSuccessCrudUpdateCrudTable(GLOBAL));
        }).orElse(() -> {
            throwValidationError(messages -> messages.addErrorsCrudCouldNotFindCrudTable(GLOBAL, form.id), () -> asEditHtml());
        });
        return redirect(getClass());
    }

    @Execute
    public HtmlResponse delete(final EditForm form) {
        verifyCrudMode(form.crudMode, CrudMode.DETAILS);
        validate(form, messages -> {}, () -> asDetailsHtml());
        verifyToken(() -> asDetailsHtml());
        final String id = form.id;
        scheduledJobService.getScheduledJob(id).ifPresent(entity -> {
            scheduledJobService.delete(entity);
            saveInfo(messages -> messages.addSuccessCrudDeleteCrudTable(GLOBAL));
        }).orElse(() -> {
            throwValidationError(messages -> messages.addErrorsCrudCouldNotFindCrudTable(GLOBAL, id), () -> asDetailsHtml());
        });
        return redirect(getClass());
    }

    @Execute
    public HtmlResponse start(final EditForm form) {
        verifyCrudMode(form.crudMode, CrudMode.DETAILS);
        validate(form, messages -> {}, () -> asDetailsHtml());
        verifyToken(() -> asDetailsHtml());
        final String id = form.id;
        scheduledJobService.getScheduledJob(id).ifPresent(entity -> {
            try {
                entity.start();
                saveInfo(messages -> messages.addSuccessJobStarted(GLOBAL, entity.getName()));
            } catch (final Exception e) {
                throwValidationError(messages -> {
                    messages.addErrorsFailedToStartJob(GLOBAL, entity.getName());
                }, () -> asDetailsHtml());
            }
        }).orElse(() -> {
            throwValidationError(messages -> {
                messages.addErrorsFailedToStartJob(GLOBAL, id);
            }, () -> asDetailsHtml());
        });
        return redirect(getClass());
    }

    @Execute
    public HtmlResponse stop(final EditForm form) {
        verifyCrudMode(form.crudMode, CrudMode.DETAILS);
        validate(form, messages -> {}, () -> asDetailsHtml());
        verifyToken(() -> asDetailsHtml());
        final String id = form.id;
        scheduledJobService.getScheduledJob(id).ifPresent(entity -> {
            try {
                final JobExecutor jobExecutoer = jobHelper.getJobExecutoer(entity.getId());
                jobExecutoer.shutdown();
                saveInfo(messages -> messages.addSuccessJobStopped(GLOBAL, entity.getName()));
            } catch (final Exception e) {
                throwValidationError(messages -> {
                    messages.addErrorsFailedToStopJob(GLOBAL, entity.getName());
                }, () -> asDetailsHtml());
            }
        }).orElse(() -> {
            throwValidationError(messages -> {
                messages.addErrorsFailedToStartJob(GLOBAL, id);
            }, () -> asDetailsHtml());
        });
        return redirect(getClass());
    }

    // ===================================================================================
    //                                                                        Assist Logic
    //                                                                        ============
    protected void loadScheduledJob(final EditForm form, ScheduledJob entity) {
        copyBeanToBean(entity, form, op -> op.exclude("crudMode").excludeNull());
        form.jobLogging = entity.isLoggingEnabled() ? Constants.ON : null;
        form.crawler = entity.isCrawlerJob() ? Constants.ON : null;
        form.available = entity.isEnabled() ? Constants.ON : null;
    }

    private OptionalEntity<ScheduledJob> getEntity(final CreateForm form, final String username, final long currentTime) {
        switch (form.crudMode) {
        case CrudMode.CREATE:
            if (form instanceof CreateForm) {
                return OptionalEntity.of(new ScheduledJob()).map(entity -> {
                    entity.setCreatedBy(username);
                    entity.setCreatedTime(currentTime);
                    return entity;
                });
            }
            break;
        case CrudMode.EDIT:
            if (form instanceof EditForm) {
                return scheduledJobService.getScheduledJob(((EditForm) form).id);
            }
            break;
        default:
            break;
        }
        return OptionalEntity.empty();
    }

    protected OptionalEntity<ScheduledJob> getScheduledJob(final CreateForm form) {
        final String username = systemHelper.getUsername();
        final long currentTime = systemHelper.getCurrentTimeAsLong();
        return getEntity(form, username, currentTime).map(entity -> {
            entity.setUpdatedBy(username);
            entity.setUpdatedTime(currentTime);
            copyBeanToBean(form, entity, op -> op.exclude(Constants.COMMON_CONVERSION_RULE));
            entity.setJobLogging(Constants.ON.equals(form.jobLogging) ? Constants.T : Constants.F);
            entity.setCrawler(Constants.ON.equals(form.crawler) ? Constants.T : Constants.F);
            entity.setAvailable(Constants.ON.equals(form.available) ? Constants.T : Constants.F);
            return entity;
        });
    }

    // ===================================================================================
    //                                                                        Small Helper
    //                                                                        ============
    protected void verifyCrudMode(final int crudMode, final int expectedMode) {
        if (crudMode != expectedMode) {
            throwValidationError(messages -> {
                messages.addErrorsCrudInvalidMode(GLOBAL, String.valueOf(expectedMode), String.valueOf(crudMode));
            }, () -> asListHtml());
        }
    }

    // ===================================================================================
    //                                                                              JSP
    //                                                                           =========

    private HtmlResponse asListHtml() {
        return asHtml(path_AdminScheduler_AdminSchedulerJsp).renderWith(data -> {
            data.register("scheduledJobItems", scheduledJobService.getScheduledJobList(scheduledJobPager)); // page navi
            }).useForm(SearchForm.class, setup -> {
            setup.setup(form -> {
                copyBeanToBean(scheduledJobPager, form, op -> op.include("id"));
            });
        });
    }

    private HtmlResponse asEditHtml() {
        return asHtml(path_AdminScheduler_AdminSchedulerEditJsp);
    }

    private HtmlResponse asDetailsHtml() {
        return asHtml(path_AdminScheduler_AdminSchedulerDetailsJsp);
    }
}
