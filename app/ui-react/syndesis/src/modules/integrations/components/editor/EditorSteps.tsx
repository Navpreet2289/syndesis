import * as H from '@syndesis/history';
import { ConnectionOverview } from '@syndesis/models';
import {
  ConnectionCard,
  ConnectionsGrid,
  ConnectionsGridCell,
  ConnectionSkeleton,
} from '@syndesis/ui';
import { WithLoader } from '@syndesis/utils';
import * as React from 'react';
import { Translation } from 'react-i18next';
import { ApiError, EntityIcon } from '../../../../shared';
import { IUIStep } from './interfaces';

export interface IEditorStepsProps {
  error: boolean;
  loading: boolean;
  steps: IUIStep[];

  getConnectionHref(connection: ConnectionOverview): H.LocationDescriptor;
  getConnectionEditHref?(connection: ConnectionOverview): H.LocationDescriptor;
}

export class EditorSteps extends React.Component<IEditorStepsProps> {
  public render() {
    return (
      <Translation ns={['connections', 'shared']}>
        {t => (
          <ConnectionsGrid>
            <WithLoader
              error={this.props.error}
              loading={this.props.loading}
              loaderChildren={
                <>
                  {new Array(5).fill(0).map((_, index) => (
                    <ConnectionsGridCell key={index}>
                      <ConnectionSkeleton />
                    </ConnectionsGridCell>
                  ))}
                </>
              }
              errorChildren={<ApiError />}
            >
              {() => {
                return this.props.steps.map((s, index) => {
                  const configurationRequired =
                    s.board &&
                    (s.board!.notices ||
                      s.board!.warnings ||
                      s.board!.errors)! > 0;

                  const isTechPreview =
                    s.connector! && s.connector!.metadata!
                      ? s.connector!.metadata!['tech-preview'] === 'true'
                      : false;

                  return (
                    <ConnectionsGridCell key={index}>
                      <ConnectionCard
                        name={s.name}
                        configurationRequired={configurationRequired}
                        description={s.description || ''}
                        icon={<EntityIcon entity={s} alt={s.name} width={46} />}
                        href={this.props.getConnectionHref(s)}
                        i18nCannotDelete={t('cannotDelete')}
                        i18nConfigurationRequired={t('configurationRequired')}
                        i18nTechPreview={t('techPreview')}
                        techPreview={isTechPreview}
                        techPreviewPopoverHtml={
                          <span
                            dangerouslySetInnerHTML={{
                              __html: t('techPreviewPopoverHtml'),
                            }}
                          />
                        }
                      />
                    </ConnectionsGridCell>
                  );
                });
              }}
            </WithLoader>
          </ConnectionsGrid>
        )}
      </Translation>
    );
  }
}
