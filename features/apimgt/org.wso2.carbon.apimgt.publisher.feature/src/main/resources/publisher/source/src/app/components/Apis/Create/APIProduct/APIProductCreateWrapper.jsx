/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import React, { useReducer, useState } from 'react';
import PropTypes from 'prop-types';
import Paper from '@material-ui/core/Paper';
import Grid from '@material-ui/core/Grid';
import { FormattedMessage } from 'react-intl';
import Stepper from '@material-ui/core/Stepper';
import Step from '@material-ui/core/Step';
import StepLabel from '@material-ui/core/StepLabel';
import Button from '@material-ui/core/Button';
import Typography from '@material-ui/core/Typography';
import { Link } from 'react-router-dom';
import APIProduct from 'AppData/APIProduct';
import Alert from 'AppComponents/Shared/Alert';
import CircularProgress from '@material-ui/core/CircularProgress';
import APICreateProductBase from 'AppComponents/Apis/Create/Components/APICreateProductBase';
import DefaultAPIForm from 'AppComponents/Apis/Create/Components/DefaultAPIForm';
import { useAppContext } from 'AppComponents/Shared/AppContext';
import ProductResourcesEditWorkspace from 'AppComponents/Apis/Details/ProductResources/ProductResourcesEditWorkspace';


/**
 * Handle API creation from GraphQL Definition.
 *
 * @export
 * @param {*} props
 * @returns
 */
export default function ApiProductCreateWrapper(props) {
    const { history } = props;
    const [wizardStep, setWizardStep] = useState(0);
    const [apiResources, setApiResources] = useState([]);
    const { settings } = useAppContext();

    const pageTitle = (
        <React.Fragment>
            <Typography variant='h5'>
                <FormattedMessage
                    id='Apis.Create.APIProduct.APIProductCreateWrapper.heading'
                    defaultMessage='Create an API Product'
                />
            </Typography>
            <Typography variant='caption'>
                <FormattedMessage
                    id='Apis.Create.APIProduct.APIProductCreateWrapper.sub.heading'
                    defaultMessage={
                        'Create an API Product providing Name, Context parameters, Resources' +
                        ' and optionally business plans'
                    }
                />
            </Typography>
        </React.Fragment>
    );
    /**
     *
     * Reduce the events triggered from API input fields to current state
     * @param {*} currentState
     * @param {*} inputAction
     * @returns
     */
    function apiInputsReducer(currentState, inputAction) {
        const { action, value } = inputAction;
        switch (action) {
            case 'type':
            case 'name':
            case 'context':
            case 'version':
            case 'policies':
            case 'isFormValid':
                return { ...currentState, [action]: value };
            case 'apiResources':
                return { ...currentState, [action]: value };
            case 'preSetAPI':
                return {
                    ...currentState,
                    name: value.name.replace(/[&/\\#,+()$~%.'":*?<>{}\s]/g, ''),
                    context: value.context,
                };
            default:
                return currentState;
        }
    }

    const [apiInputs, inputsDispatcher] = useReducer(apiInputsReducer, {
        type: 'ApiProductCreateWrapper',
        inputValue: '',
        formValidity: false,
    });

    /**
     *
     *
     * @param {*} event
     */
    function handleOnChange(event) {
        const { name: action, value } = event.target;
        inputsDispatcher({ action, value });
    }

    /**
     *
     * Set the validity of the API Inputs form
     * @param {*} isValidForm
     * @param {*} validationState
     */
    function handleOnValidate(isFormValid) {
        inputsDispatcher({
            action: 'isFormValid',
            value: isFormValid,
        });
    }

    const [isCreating, setCreating] = useState();

    const createAPIProduct = () => {
        {
            setCreating(true);
            const {
                name, context, policies,
            } = apiInputs;
            const apiData = {
                name,
                context,
                policies,
                apis: apiResources,
            };
            apiData.gatewayEnvironments = settings.environment.map(env => env.name);
            apiData.transport = ['http', 'https'];
            const newAPIProduct = new APIProduct(apiData);
            newAPIProduct
                .saveProduct(apiData)
                .then((apiProduct) => {
                    Alert.info('API Product created successfully');
                    history.push(`/api-products/${apiProduct.id}/overview`);
                })
                .catch((error) => {
                    if (error.response) {
                        Alert.error(error.response.body.description);
                    } else {
                        Alert.error('Something went wrong while adding the API Product');
                    }
                })
                .finally(() => setCreating(false));
        }
    };

    return (
        <React.Fragment>
            <APICreateProductBase
                title={pageTitle}
            >
                <Paper>
                    {wizardStep === 0 && (
                        <Stepper activeStep={0}>
                            <Step>
                                <StepLabel>
                                    <FormattedMessage
                                        id='Apis.Create.APIProduct.APIProductCreateWrapper.productDetails'
                                        defaultMessage='Provide Product Details'
                                    />
                                </StepLabel>
                            </Step>

                            <Step>
                                <StepLabel>
                                    <FormattedMessage
                                        id='Apis.Create.APIProduct.APIProductCreateWrapper.resources'
                                        defaultMessage='Add Resources'
                                    />
                                </StepLabel>
                            </Step>
                        </Stepper>
                    )}
                    {wizardStep === 1 && (
                        <Stepper activeStep={1}>
                            <Step>
                                <StepLabel>
                                    <FormattedMessage
                                        id='Apis.Create.APIProduct.APIProductCreateWrapper.productDetails'
                                        defaultMessage='Provide Product Details'
                                    />
                                </StepLabel>
                            </Step>

                            <Step>
                                <StepLabel>
                                    <FormattedMessage
                                        id='Apis.Create.APIProduct.APIProductCreateWrapper.resources'
                                        defaultMessage='Add Resources'
                                    />
                                </StepLabel>
                            </Step>
                        </Stepper>
                    )}
                </Paper>
                <Grid container spacing={3}>
                    <Grid item md={12} />
                    {wizardStep === 0 && <Grid item md={1} />}
                    <Grid item md={wizardStep === 0 ? 10 : 12}>
                        {wizardStep === 0 && (
                            <DefaultAPIForm
                                onValidate={handleOnValidate}
                                onChange={handleOnChange}
                                api={apiInputs}
                                isAPIProduct
                            />
                        )}
                        {wizardStep === 1 && (
                            <ProductResourcesEditWorkspace
                                apiResources={apiResources}
                                setApiResources={setApiResources}
                                isStateCreate
                            />
                        )}
                    </Grid>
                    {wizardStep === 0 && <Grid item md={1} />}
                    <Grid item md={1} />
                    <Grid item md={wizardStep === 0 ? 9 : 10}>
                        <Grid container direction='row' justify='space-between'>
                            <Grid item>
                                {wizardStep === 0 && (
                                    <Link to='/apis/'>
                                        <Button variant='outlined'>
                                            <FormattedMessage
                                                id='Apis.Create.APIProduct.APIProductCreateWrapper.cancel'
                                                defaultMessage='Cancel'
                                            />
                                        </Button>
                                    </Link>
                                )}
                                {wizardStep === 1 &&
                                <Button onClick={() =>
                                    setWizardStep(step => step - 1)}
                                >Back
                                </Button>}
                            </Grid>
                            <Grid item>
                                {wizardStep === 0 && (
                                    <Button
                                        onClick={() => setWizardStep(step => step + 1)}
                                        variant='contained'
                                        color='primary'
                                        disabled={!apiInputs.isFormValid}
                                    >
                                    Next
                                    </Button>
                                )}
                                {wizardStep === 1 && (
                                    <Button
                                        variant='contained'
                                        color='primary'
                                        disabled={!apiInputs.isFormValid || isCreating || (apiResources.length === 0)}
                                        onClick={createAPIProduct}
                                    >
                                        <FormattedMessage
                                            id='Apis.Details.ProductResources.ProductResourcesEdit.save'
                                            defaultMessage='Save'
                                        />
                                        {isCreating && <CircularProgress size={24} />}
                                    </Button>
                                )}
                            </Grid>
                        </Grid>
                    </Grid>
                </Grid>
            </APICreateProductBase>
        </React.Fragment>

    );
}

ApiProductCreateWrapper.propTypes = {
    history: PropTypes.shape({ push: PropTypes.func }).isRequired,
};
