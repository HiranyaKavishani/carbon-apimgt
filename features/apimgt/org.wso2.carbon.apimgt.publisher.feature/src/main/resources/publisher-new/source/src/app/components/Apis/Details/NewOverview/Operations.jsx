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

import React from 'react';
import { withStyles, withTheme } from '@material-ui/core/styles';
import PropTypes from 'prop-types';
import Chip from '@material-ui/core/Chip';
import { Link } from 'react-router-dom';

import classNames from 'classnames';
import Paper from '@material-ui/core/Paper';
import Typography from '@material-ui/core/Typography';
import Button from '@material-ui/core/Button';
import ApiContext from '../components/ApiContext';

function RenderMethodBase(props) {
    const { theme, method } = props;
    let chipColor = theme.custom.resourceChipColors ? theme.custom.resourceChipColors[method] : null;
    let chipTextColor = '#000000';
    if (!chipColor) {
        console.log('Check the theme settings. The resourceChipColors is not populated properly');
        chipColor = '#cccccc';
    } else {
        chipTextColor = theme.palette.getContrastText(theme.custom.resourceChipColors[method]);
    }
    return <Chip label={method} style={{ backgroundColor: chipColor, color: chipTextColor, height: 15 }} />;
}

RenderMethodBase.propTypes = {
    method: PropTypes.string.isRequired,
    theme: PropTypes.shape({}).isRequired,
    classes: PropTypes.shape({}).isRequired,
};

const RenderMethod = withTheme()(RenderMethodBase);

const styles = {
    root: {
        display: 'flex',
        flexDirection: 'row',
        alignItems: 'center',
        marginBottom: 10,
    },
    heading: {
        marginRight: 20,
    },
};

function Operations(props) {
    const { parentClasses } = props;
    return (
        <ApiContext.Consumer>
            {({ api }) => (
                <Paper className={classNames({ [parentClasses.root]: true, [parentClasses.specialGap]: true })}>
                    <div className={parentClasses.titleWrapper}>
                        <Typography variant='h5' component='h3' className={parentClasses.title}>
                            Operations
                        </Typography>
                        <Link to={'/apis/' + api.id + '/operations'}>
                            <Button variant='contained' color='default'>
                                Edit
                            </Button>
                        </Link>
                    </div>

                    {/* Operations */}
                    <Typography component='p' variant='body1'>
                        {api.operations &&
                            api.operations.length !== 0 &&
                            api.operations.map((item, index) => (
                                <span>
                                     {item.target}{'  '}
                                     <RenderMethod method={item.verb} /><br/>
                                </span>
                            ))}
                    </Typography>
                </Paper>
            )}
        </ApiContext.Consumer>
    );
}

Operations.propTypes = {
    parentClasses: PropTypes.shape({}).isRequired,
};

export default withStyles(styles)(Operations);

